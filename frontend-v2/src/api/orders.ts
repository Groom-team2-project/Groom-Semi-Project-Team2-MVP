import { api } from './client';
import type { OrderResponse, PurchaseResponse } from './types';

// 단건 바로구매 — 주문을 PENDING_PAYMENT로 생성하고 재고를 예약한다
export function purchase(productId: number, quantity: number) {
  return api<PurchaseResponse>(`/api/v1/products/${productId}/orders`, {
    method: 'POST',
    body: { quantity },
    auth: true
  });
}

export function getOrder(orderId: number | string) {
  return api<OrderResponse>(`/api/v1/orders/${orderId}`, { auth: true });
}

// 내 주문 내역 — 최신순. 서버가 원본이라 기기·브라우저가 달라도 동일하게 보인다.
// (토큰의 회원 기준이므로 남의 주문이 섞이지 않는다)
export function getMyOrders() {
  return api<OrderResponse[]>('/api/v1/members/me/orders', { auth: true });
}

export function cancelOrder(orderId: number | string) {
  return api<unknown>(`/api/v1/orders/${orderId}/cancel`, { method: 'POST', auth: true });
}
