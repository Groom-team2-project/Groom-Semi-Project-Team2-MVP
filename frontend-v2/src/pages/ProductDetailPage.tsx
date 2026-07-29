import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getProduct } from '../api/products';
import { getProductReviews, createReview } from '../api/reviews';
import { addCartItem } from '../api/cart';
import { purchase } from '../api/orders';
import { ApiError } from '../api/client';
import { formatPrice, photoOf } from '../components/ProductCard';
import { useToast } from '../components/Toast';
import { tokenStore } from '../lib/auth';

export function ProductDetailPage() {
  const { productId = '' } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [quantity, setQuantity] = useState(1);
  const [content, setContent] = useState('');
  const [rating, setRating] = useState(5);

  const { data: product, isLoading, isError, error } = useQuery({
    queryKey: ['product', productId],
    queryFn: () => getProduct(productId)
  });
  const { data: reviews } = useQuery({
    queryKey: ['reviews', productId],
    queryFn: () => getProductReviews(productId)
  });

  const requireLogin = () => {
    toast('로그인이 필요해요. 우측 상단에서 로그인해주세요.', 'error');
  };

  const buyMutation = useMutation({
    mutationFn: () => purchase(Number(productId), quantity),
    onSuccess: (res) => {
      toast('주문이 생성되었어요. 결제를 진행해주세요.');
      navigate(`/orders/${res.orderId}`);
    },
    onError: (e) => handleError(e)
  });

  const cartMutation = useMutation({
    mutationFn: () => addCartItem(Number(productId), quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      toast('장바구니에 담았어요.');
    },
    onError: (e) => handleError(e)
  });

  const reviewMutation = useMutation({
    mutationFn: () => createReview({ productId: Number(productId), content, rating }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] });
      setContent('');
      toast('리뷰가 등록되었어요.');
    },
    onError: (e) => handleError(e, '리뷰는 결제 완료한 구매자만 작성할 수 있어요.')
  });

  function handleError(e: unknown, fallbackFor403?: string) {
    if (e instanceof ApiError) {
      if (e.status === 401) return requireLogin();
      if (e.status === 403 && fallbackFor403) return toast(fallbackFor403, 'error');
      if (e.errorCode === 'OUT_OF_STOCK') return toast('품절된 상품이에요.', 'error');
      return toast(e.message, 'error');
    }
    toast('요청에 실패했어요.', 'error');
  }

  if (isLoading) return <div className="spin" />;
  if (isError) {
    const message = error instanceof ApiError ? error.message : '상품 상세 요청에 실패했어요.';
    return <div className="empty">상품을 불러오지 못했어요. {message}</div>;
  }
  if (!product) return <div className="empty">상품을 찾을 수 없어요.</div>;

  const soldOut = product.availableStocks <= 0;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 40 }} className="detail-grid">
      <style>{`@media (max-width: 860px) { .detail-grid { grid-template-columns: 1fr !important; } }`}</style>

      <div className="detail-photo rise">
        <img src={photoOf(Number(productId), 900)} alt={product.productName} />
      </div>

      <div className="rise rise-1">
        <span className="eyebrow">Product</span>
        <h1 className="h-display" style={{ fontSize: 'clamp(26px,3.4vw,38px)' }}>{product.productName}</h1>
        <div className="price buy" style={{ fontSize: 28, marginTop: 12 }}>
          {formatPrice(product.productPrice)}
        </div>
        <p className="text-muted" style={{ marginTop: 6 }}>
          {soldOut ? '품절' : `구매 가능 ${product.availableStocks}개`}
        </p>

        <div className="divider" />

        <div className="field" style={{ maxWidth: 140 }}>
          <label>수량</label>
          <input
            className="input"
            type="number"
            min={1}
            max={Math.max(product.availableStocks, 1)}
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
          />
        </div>

        <div className="row" style={{ marginTop: 20 }}>
          <button
            className="btn btn-buy"
            style={{ flex: 1 }}
            disabled={soldOut || buyMutation.isPending}
            onClick={() => (tokenStore.isLoggedIn() ? buyMutation.mutate() : requireLogin())}
          >
            바로 구매 <span className="chip">↗</span>
          </button>
          <button
            className="btn btn-ghost"
            disabled={soldOut || cartMutation.isPending}
            onClick={() => (tokenStore.isLoggedIn() ? cartMutation.mutate() : requireLogin())}
          >
            장바구니
          </button>
        </div>

        <div className="divider" />

        <h2 className="h-section">리뷰 {reviews ? `(${reviews.length})` : ''}</h2>
        <div className="stack" style={{ marginTop: 14 }}>
          {(reviews ?? []).length === 0 && (
            <p className="text-muted">아직 리뷰가 없어요. 첫 구매자가 되어보세요.</p>
          )}
          {(reviews ?? []).map((r) => (
            <div key={r.reviewId} className="bezel">
              <div className="core" style={{ padding: 16 }}>
                <div className="row between">
                  <strong style={{ fontSize: 13 }}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</strong>
                  <span className="text-muted" style={{ fontSize: 12 }}>
                    {new Date(r.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <p style={{ fontSize: 14, marginTop: 8 }}>{r.content}</p>
              </div>
            </div>
          ))}
        </div>

        <form
          className="stack"
          style={{ marginTop: 18 }}
          onSubmit={(e) => {
            e.preventDefault();
            if (!tokenStore.isLoggedIn()) return requireLogin();
            if (!content.trim()) return toast('리뷰 내용을 입력해주세요.', 'error');
            reviewMutation.mutate();
          }}
        >
          <div className="row">
            <select className="input" value={rating} onChange={(e) => setRating(Number(e.target.value))} style={{ width: 110 }}>
              {[5, 4, 3, 2, 1].map((n) => (
                <option key={n} value={n}>★ {n}</option>
              ))}
            </select>
            <input
              className="input"
              style={{ flex: 1 }}
              placeholder="구매자만 리뷰를 남길 수 있어요"
              value={content}
              onChange={(e) => setContent(e.target.value)}
            />
            <button className="btn btn-primary btn-sm" disabled={reviewMutation.isPending}>등록</button>
          </div>
        </form>
      </div>
    </div>
  );
}
