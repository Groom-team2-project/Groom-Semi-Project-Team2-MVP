import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMe, updateMe } from '../api/members';
import { getMyOrders } from '../api/orders';
import { ApiError } from '../api/client';
import { formatPrice } from '../components/ProductCard';
import { StatusBadge } from '../components/StatusBadge';
import { useToast } from '../components/Toast';
import { tokenStore } from '../lib/auth';
import { startKakaoLogin } from '../api/auth';
import type { OrderResponse } from '../api/types';

// 백엔드가 타임존 없는 LocalDateTime 을 주므로 브라우저 로컬 형식으로 표시한다.
// 파싱에 실패하면 원본 문자열을 그대로 보여준다 — 조용히 빈칸으로 만들지 않는다.
function formatDateTime(value: string | null) {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

// "티셔츠 외 2건" — 목록에서는 품목을 전부 펼치지 않고 요약만 보여준다.
function summarizeItems(order: OrderResponse) {
  const [first, ...rest] = order.orderItems;
  if (!first) return '상품 정보 없음';
  return rest.length > 0 ? `${first.productName} 외 ${rest.length}건` : first.productName;
}

function OrderRow({ order }: { order: OrderResponse }) {
  const expired =
    order.paymentExpiresAt !== null && new Date(order.paymentExpiresAt).getTime() < Date.now();

  return (
    <div className="bezel">
      <div className="core row between" style={{ padding: 18, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <div className="row" style={{ gap: 10 }}>
            <Link to={`/orders/${order.orderId}`}>
              <strong style={{ fontSize: 15 }}>주문 #{order.orderId}</strong>
            </Link>
            <StatusBadge status={order.status} />
          </div>
          <p className="text-muted" style={{ fontSize: 13, marginTop: 4 }}>
            {summarizeItems(order)} · {formatDateTime(order.createdAt)}
          </p>

          {/* 결제 대기 주문은 마감이 지나면 예약 재고가 회수되므로 남은 시간을 알려준다 */}
          {order.status === 'PENDING_PAYMENT' && order.paymentExpiresAt && (
            <p className="text-muted" style={{ fontSize: 12, marginTop: 2 }}>
              {expired
                ? '결제 시간이 지나 곧 취소돼요.'
                : `결제 마감 ${formatDateTime(order.paymentExpiresAt)}`}
            </p>
          )}
          {order.status === 'CANCELED' && order.canceledAt && (
            <p className="text-muted" style={{ fontSize: 12, marginTop: 2 }}>
              취소 {formatDateTime(order.canceledAt)}
            </p>
          )}
        </div>

        <div className="row" style={{ gap: 14 }}>
          <b className="price">{formatPrice(order.totalPrice)}</b>
          <Link to={`/orders/${order.orderId}`} className="btn btn-ghost btn-sm">
            상세
          </Link>
        </div>
      </div>
    </div>
  );
}

export function MyPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const loggedIn = tokenStore.isLoggedIn();
  const [nickname, setNickname] = useState('');

  const { data: me, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: getMe,
    enabled: loggedIn
  });

  const { data: orders, isLoading: ordersLoading } = useQuery({
    queryKey: ['myOrders'],
    queryFn: getMyOrders,
    enabled: loggedIn
  });

  const updateMutation = useMutation({
    mutationFn: () => updateMe({ nickname: nickname.trim() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me'] });
      toast('프로필이 수정되었어요.');
      setNickname('');
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : '수정에 실패했어요.', 'error')
  });

  if (!loggedIn) {
    return (
      <div className="empty">
        <p>로그인 후 이용할 수 있어요.</p>
        <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={() => startKakaoLogin()}>
          카카오로 로그인
        </button>
      </div>
    );
  }
  if (isLoading) return <div className="spin" />;

  return (
    <>
      <header style={{ marginBottom: 32 }} className="rise">
        <span className="eyebrow">My Page</span>
        <h1 className="h-display" style={{ fontSize: 'clamp(28px,4vw,40px)' }}>
          {me?.nickname ?? '회원'}님, 반가워요
        </h1>
      </header>

      <div className="bezel rise rise-1" style={{ maxWidth: 520 }}>
        <div className="core stack">
          <div className="row between">
            <span className="text-muted">이메일</span>
            <b style={{ fontSize: 14 }}>{me?.email ?? '-'}</b>
          </div>
          <div className="row between">
            <span className="text-muted">닉네임</span>
            <b style={{ fontSize: 14 }}>{me?.nickname ?? '-'}</b>
          </div>
          <div className="row between">
            <span className="text-muted">가입 경로</span>
            <b style={{ fontSize: 14 }}>{me?.provider ?? '-'}</b>
          </div>
          <div className="divider" style={{ margin: '8px 0' }} />
          <form
            className="row"
            onSubmit={(e) => {
              e.preventDefault();
              if (!nickname.trim()) return toast('새 닉네임을 입력해주세요.', 'error');
              updateMutation.mutate();
            }}
          >
            <input
              className="input"
              style={{ flex: 1 }}
              placeholder="새 닉네임"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
            />
            <button className="btn btn-primary btn-sm" disabled={updateMutation.isPending}>변경</button>
          </form>
        </div>
      </div>

      <section style={{ marginTop: 40 }} className="rise rise-2">
        <div className="row between" style={{ marginBottom: 16 }}>
          <h2 className="h-section" style={{ margin: 0 }}>주문 내역</h2>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['myOrders'] })}
          >
            새로고침
          </button>
        </div>

        {ordersLoading ? (
          <div className="spin" />
        ) : !orders || orders.length === 0 ? (
          <div className="empty">아직 주문이 없어요.</div>
        ) : (
          <div className="stack">
            {orders.map((order) => (
              <OrderRow key={order.orderId} order={order} />
            ))}
          </div>
        )}
      </section>
    </>
  );
}
