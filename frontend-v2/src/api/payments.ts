import { api } from './client';
import type { PaymentResponse, RefundResponse } from './types';

// 토스 승인(confirm) — 결제 성공 리다이렉트 후 호출. 금액은 서버가 보관한 주문 금액으로 승인된다.
// tossOrderId는 결제창 호출에 사용한 주문번호(ORDER_{pk}_{시도})와 정확히 같아야 승인된다.
export function confirmPayment(
  orderId: number | string,
  paymentKey: string,
  tossOrderId: string,
  method: string
) {
  return api<PaymentResponse>(`/api/v1/orders/${orderId}/payments`, {
    method: 'POST',
    body: { paymentKey, tossOrderId, method },
    auth: true
  });
}

export function refundPayment(orderId: number | string, cancelReason: string) {
  return api<RefundResponse>(`/api/v1/orders/${orderId}/payments/refund`, {
    method: 'POST',
    body: { cancelReason },
    auth: true
  });
}
