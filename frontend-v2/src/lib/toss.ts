import { loadTossPayments } from '@tosspayments/tosspayments-sdk';

const CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;

// successUrl 리다이렉트 후에도 결제 수단을 알 수 있게 세션에 보관
export const PAYMENT_METHOD_KEY = 'toss_payment_method';

export type TossMethod = 'CARD' | 'TRANSFER' | 'EASY_PAY';

// 토스 결제창 호출 — orderId는 백엔드와 합의된 `ORDER_{주문PK}` 규칙 (토스 6자 제약 대응)
export async function requestTossPayment(params: {
  orderPk: number | string;
  amount: number;
  orderName: string;
  method: TossMethod;
}): Promise<void> {
  if (!CLIENT_KEY) {
    throw new Error('VITE_TOSS_CLIENT_KEY가 없습니다. frontend-v2/.env를 확인해주세요.');
  }
  sessionStorage.setItem(PAYMENT_METHOD_KEY, params.method);

  const toss = await loadTossPayments(CLIENT_KEY);
  const payment = toss.payment({ customerKey: 'ANONYMOUS' });

  await payment.requestPayment({
    method: params.method,
    amount: { currency: 'KRW', value: params.amount },
    orderId: `ORDER_${params.orderPk}`,
    orderName: params.orderName,
    successUrl: `${window.location.origin}/payment/success`,
    failUrl: `${window.location.origin}/payment/fail`
  } as unknown as Parameters<typeof payment.requestPayment>[0]);
}
