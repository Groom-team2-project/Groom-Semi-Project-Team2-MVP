import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMe, updateMe } from '../api/members';
import { getMyOrders } from '../api/orders';
import { ApiError } from '../api/client';
import { useToast } from '../components/Toast';
import { tokenStore } from '../lib/auth';
import { startKakaoLogin } from '../api/auth';
import { StatusBadge } from '../components/StatusBadge';
import { formatPrice } from '../components/ProductCard';
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

// 결제 대기 주문에만 붙는 한 줄. 마감이 지나면 곧 서버가 취소하므로 그 사실을 알린다.
function OrderNote({ order }: { order: OrderResponse }) {
  if (order.status === 'PENDING_PAYMENT' && order.paymentExpiresAt) {
    const expired = new Date(order.paymentExpiresAt).getTime() <= Date.now();
    return (
      <p className="text-muted" style={{ fontSize: 12, marginTop: 2 }}>
        {expired ? '결제 시간이 지나 곧 취소돼요.' : `결제 마감 ${formatDateTime(order.paymentExpiresAt)}`}
      </p>
    );
  }
  if (order.status === 'CANCELED' && order.canceledAt) {
    return (
      <p className="text-muted" style={{ fontSize: 12, marginTop: 2 }}>
        취소 {formatDateTime(order.canceledAt)}
      </p>
    );
  }
  return null;
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

  const {
    data: orders,
    isLoading: isOrdersLoading,
    isError: isOrdersError,
    error: ordersError,
    refetch: refetchOrders
  } = useQuery({
    queryKey: ['my-orders'],
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

      <section className="bezel rise rise-2" style={{ marginTop: 24 }}>
        <div className="core">
          <div className="row between" style={{ marginBottom: 14 }}>
            <h2 className="h-section">내 주문 내역</h2>
            <button className="btn btn-ghost btn-sm" onClick={() => refetchOrders()}>
              새로고침
            </button>
          </div>

          {isOrdersLoading ? (
            <div className="spin" />
          ) : isOrdersError ? (
            <div className="empty">
              <p>주문 내역을 불러오지 못했어요. {ordersError instanceof ApiError ? ordersError.message : ''}</p>
              <button className="btn btn-ghost btn-sm" style={{ marginTop: 12 }} onClick={() => refetchOrders()}>
                다시 시도
              </button>
            </div>
          ) : !orders?.length ? (
            <p className="text-muted">아직 주문 내역이 없어요.</p>
          ) : (
            <div className="stack">
              {orders.map((order) => (
                <Link
                  key={order.orderId}
                  to={`/orders/${order.orderId}`}
                  className="row between"
                  style={{ padding: '14px 0', borderTop: '1px solid var(--line)' }}
                >
                  <div>
                    <strong>주문 #{order.orderId}</strong>
                    <p className="text-muted" style={{ fontSize: 13, marginTop: 4 }}>
                      {summarizeItems(order)} · {formatDateTime(order.createdAt)} ·{' '}
                      {formatPrice(order.totalPrice)}
                    </p>
                    <OrderNote order={order} />
                  </div>
                  <StatusBadge status={order.status} />
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>
    </>
  );
}
