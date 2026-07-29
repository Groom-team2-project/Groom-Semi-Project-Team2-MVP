import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getOrder, cancelOrder } from '../api/orders';
import { refundPayment } from '../api/payments';
import { ApiError } from '../api/client';
import { requestTossPayment, type TossMethod } from '../lib/toss';
import { formatPrice, photoOf } from '../components/ProductCard';
import { StatusBadge } from '../components/StatusBadge';
import { useToast } from '../components/Toast';

const METHODS: { key: TossMethod; label: string }[] = [
  { key: 'CARD', label: '카드' },
  { key: 'TRANSFER', label: '계좌이체' },
  { key: 'EASY_PAY', label: '간편결제' }
];

export function OrderDetailPage() {
  const { orderId = '' } = useParams();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [method, setMethod] = useState<TossMethod>('CARD');
  const [paying, setPaying] = useState(false);

  const { data: order, isLoading } = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => getOrder(orderId)
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['order', orderId] });

  const refundMutation = useMutation({
    mutationFn: () => refundPayment(orderId, '고객 환불 요청'),
    onSuccess: () => {
      invalidate();
      toast('환불이 완료되었어요. 재고가 복구됩니다.');
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : '환불에 실패했어요.', 'error')
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(orderId),
    onSuccess: () => {
      invalidate();
      toast('주문이 취소되었어요.');
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : '취소에 실패했어요.', 'error')
  });

  async function startPayment() {
    if (!order) return;
    setPaying(true);
    try {
      await requestTossPayment({
        orderPk: order.orderId,
        amount: order.totalPrice,
        orderName:
          order.orderItems.length > 1
            ? `${order.orderItems[0]?.productName} 외 ${order.orderItems.length - 1}건`
            : order.orderItems[0]?.productName ?? `SOLDOUT 주문 #${order.orderId}`,
        method
      });
    } catch (e) {
      // 사용자가 결제창을 닫은 경우 등 — 주문은 PENDING 유지라 재시도 가능
      toast(e instanceof Error ? e.message : '결제창 호출에 실패했어요.', 'error');
    } finally {
      setPaying(false);
    }
  }

  if (isLoading) return <div className="spin" />;
  if (!order) return <div className="empty">주문을 찾을 수 없어요. 로그인 상태를 확인해주세요.</div>;

  return (
    <>
      <header className="row between rise" style={{ marginBottom: 32, flexWrap: 'wrap', gap: 16 }}>
        <div>
          <span className="eyebrow">Order</span>
          <h1 className="h-display" style={{ fontSize: 'clamp(28px,4vw,40px)' }}>주문 #{order.orderId}</h1>
          <p className="text-muted" style={{ marginTop: 4 }}>
            {new Date(order.createdAt).toLocaleString()}
          </p>
        </div>
        <StatusBadge status={order.status} />
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: '1.7fr 1fr', gap: 28 }} className="order-grid">
        <style>{`@media (max-width: 860px) { .order-grid { grid-template-columns: 1fr !important; } }`}</style>

        <div className="stack rise rise-1">
          {order.orderItems.map((item) => (
            <div className="bezel" key={item.orderItemId}>
              <div className="core row between" style={{ padding: 18 }}>
                <div className="row" style={{ gap: 14 }}>
                  <img className="thumb" src={photoOf(item.productId, 200)} alt="" />
                  <div>
                    <Link to={`/products/${item.productId}`}>
                      <strong style={{ fontSize: 15 }}>{item.productName}</strong>
                    </Link>
                    <p className="text-muted" style={{ fontSize: 13 }}>
                      {formatPrice(item.orderPrice)} × {item.quantity}개
                    </p>
                  </div>
                </div>
                <b className="price">{formatPrice(item.itemTotalPrice)}</b>
              </div>
            </div>
          ))}
        </div>

        <aside className="bezel rise rise-2" style={{ alignSelf: 'start' }}>
          <div className="core">
            <h2 className="h-section">결제 정보</h2>
            <div className="divider" />
            <div className="row between">
              <span className="text-muted">총 결제 금액</span>
              <b className="price buy" style={{ fontSize: 22 }}>{formatPrice(order.totalPrice)}</b>
            </div>

            {order.status === 'PENDING_PAYMENT' && (
              <>
                <div className="row" style={{ marginTop: 18 }}>
                  {METHODS.map((m) => (
                    <button
                      key={m.key}
                      className={`btn btn-sm ${method === m.key ? 'btn-primary' : 'btn-ghost'}`}
                      onClick={() => setMethod(m.key)}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>
                <button
                  className="btn btn-buy"
                  style={{ width: '100%', marginTop: 16 }}
                  disabled={paying}
                  onClick={startPayment}
                >
                  결제하기 <span className="chip">↗</span>
                </button>
                <button
                  className="btn btn-danger btn-sm"
                  style={{ width: '100%', marginTop: 10 }}
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate()}
                >
                  주문 취소
                </button>
                <p className="text-muted" style={{ fontSize: 12, marginTop: 12 }}>
                  테스트 환경이라 실제 돈은 출금되지 않아요.
                </p>
              </>
            )}

            {order.status === 'COMPLETED' && (
              <>
                <button
                  className="btn btn-danger"
                  style={{ width: '100%', marginTop: 18 }}
                  disabled={refundMutation.isPending}
                  onClick={() => refundMutation.mutate()}
                >
                  환불하기
                </button>
                <p className="text-muted" style={{ fontSize: 12, marginTop: 12 }}>
                  환불 시 토스 결제가 취소되고 재고가 복구돼요.
                </p>
              </>
            )}

            {order.status === 'CANCELED' && (
              <p className="text-muted" style={{ marginTop: 16, fontSize: 13 }}>
                취소된 주문이에요. {order.canceledAt && `(${new Date(order.canceledAt).toLocaleString()})`}
              </p>
            )}

            <Link to="/me" className="btn btn-primary btn-sm" style={{ width: '100%', marginTop: 14 }}>
              내 주문 내역 보기
            </Link>
            <Link to="/" className="btn btn-ghost btn-sm" style={{ width: '100%', marginTop: 14 }}>
              계속 쇼핑하기
            </Link>
          </div>
        </aside>
      </div>
    </>
  );
}
