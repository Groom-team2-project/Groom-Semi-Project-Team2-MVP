import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getCart, updateCartItem, removeCartItem, checkoutCart } from '../api/cart';
import { ApiError } from '../api/client';
import { formatPrice, photoOf } from '../components/ProductCard';
import { useToast } from '../components/Toast';
import { tokenStore } from '../lib/auth';

export function CartPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const loggedIn = tokenStore.isLoggedIn();

  const { data: cart, isLoading } = useQuery({
    queryKey: ['cart'],
    queryFn: getCart,
    enabled: loggedIn
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['cart'] });

  const updateMutation = useMutation({
    mutationFn: ({ id, qty }: { id: number; qty: number }) => updateCartItem(id, qty),
    onSuccess: invalidate,
    onError: (e) => toast(e instanceof ApiError ? e.message : '수량 변경에 실패했어요.', 'error')
  });

  const removeMutation = useMutation({
    mutationFn: (id: number) => removeCartItem(id),
    onSuccess: () => { invalidate(); toast('삭제했어요.'); },
    onError: () => toast('삭제에 실패했어요.', 'error')
  });

  const checkoutMutation = useMutation({
    mutationFn: checkoutCart,
    onSuccess: (res) => {
      invalidate();
      // 방금 만든 주문이 마이페이지 주문 내역에 바로 보이도록 함께 무효화한다.
      queryClient.invalidateQueries({ queryKey: ['my-orders'] });
      toast('주문이 생성되었어요. 결제를 진행해주세요.');
      navigate(`/orders/${res.orderId}`);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.errorCode === 'OUT_OF_STOCK') return toast('품절된 상품이 있어요.', 'error');
      toast(e instanceof ApiError ? e.message : '주문 생성에 실패했어요.', 'error');
    }
  });

  if (!loggedIn) return <div className="empty">장바구니는 로그인 후 이용할 수 있어요.</div>;
  if (isLoading) return <div className="spin" />;

  const items = cart?.items ?? [];

  return (
    <>
      <header style={{ marginBottom: 32 }} className="rise">
        <span className="eyebrow">Cart</span>
        <h1 className="h-display" style={{ fontSize: 'clamp(28px,4vw,40px)' }}>장바구니</h1>
      </header>

      {items.length === 0 ? (
        <div className="empty">장바구니가 비어 있어요.</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '1.7fr 1fr', gap: 28 }} className="cart-grid">
          <style>{`@media (max-width: 860px) { .cart-grid { grid-template-columns: 1fr !important; } }`}</style>

          <div className="stack rise rise-1">
            {items.map((item) => (
              <div className="bezel" key={item.cartItemId}>
                <div className="core row between" style={{ padding: 18 }}>
                  <div className="row" style={{ gap: 14 }}>
                    <img className="thumb" src={photoOf(item.productId, 200)} alt="" />
                    <div>
                      <strong style={{ fontSize: 15 }}>{item.productName}</strong>
                      <p className="text-muted" style={{ fontSize: 13 }}>
                        {formatPrice(item.productPrice)} · 합계 <b>{formatPrice(item.lineTotal)}</b>
                      </p>
                    </div>
                  </div>
                  <div className="row">
                    <button
                      className="btn btn-ghost btn-sm"
                      disabled={item.quantity <= 1 || updateMutation.isPending}
                      onClick={() => updateMutation.mutate({ id: item.cartItemId, qty: item.quantity - 1 })}
                    >−</button>
                    <span style={{ minWidth: 24, textAlign: 'center', fontWeight: 700 }}>{item.quantity}</span>
                    <button
                      className="btn btn-ghost btn-sm"
                      disabled={updateMutation.isPending}
                      onClick={() => updateMutation.mutate({ id: item.cartItemId, qty: item.quantity + 1 })}
                    >+</button>
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => removeMutation.mutate(item.cartItemId)}
                    >삭제</button>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <aside className="bezel rise rise-2" style={{ alignSelf: 'start' }}>
            <div className="core">
              <h2 className="h-section">주문 요약</h2>
              <div className="divider" />
              <div className="row between"><span className="text-muted">총 수량</span><b>{cart?.totalQuantity}개</b></div>
              <div className="row between" style={{ marginTop: 8 }}>
                <span className="text-muted">총 금액</span>
                <b className="price buy" style={{ fontSize: 20 }}>{formatPrice(cart?.totalPrice ?? 0)}</b>
              </div>
              <button
                className="btn btn-buy"
                style={{ width: '100%', marginTop: 20 }}
                disabled={checkoutMutation.isPending}
                onClick={() => checkoutMutation.mutate()}
              >
                전체 주문하기 <span className="chip">↗</span>
              </button>
              <p className="text-muted" style={{ fontSize: 12, marginTop: 12 }}>
                주문 생성 시 재고가 예약되고, 결제 완료 시 확정돼요.
              </p>
            </div>
          </aside>
        </div>
      )}
    </>
  );
}
