import { loadTossPayments } from '@tosspayments/tosspayments-sdk';

// 토스 클라이언트 키 (프론트 전용, 공개 가능). frontend/.env 의 VITE_TOSS_CLIENT_KEY 로 주입한다.
const CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;

// 결제 승인(confirm) 시 다시 필요한 결제 수단을 successUrl 이동 후에도 읽기 위해 저장하는 키
export const PAYMENT_METHOD_KEY = 'toss_payment_method';

export type TossMethod = 'CARD' | 'TRANSFER';

/**
 * 토스 결제창을 띄운다. 성공 시 토스가 successUrl 로 리다이렉트하며
 * paymentKey / orderId / amount 를 쿼리로 넘겨준다.
 *
 * orderId 는 백엔드와 약속한 형식(`ORDER_{주문PK}`)으로 보낸다.
 * (토스는 orderId 가 6~64자여야 하므로 짧은 주문 PK 를 그대로 못 쓴다)
 */
export async function requestTossPayment(params: {
  orderPk: number | string;
  amount: number;
  orderName: string;
  method: TossMethod;
}): Promise<void> {
  if (!CLIENT_KEY) {
    throw new Error(
      'VITE_TOSS_CLIENT_KEY 가 없습니다. frontend/.env 에 토스 클라이언트 키를 넣어주세요.'
    );
  }

  // 결제 수단은 successUrl 로 이동하면 화면 상태가 초기화되므로 세션에 저장해 둔다
  sessionStorage.setItem(PAYMENT_METHOD_KEY, params.method);

  const tossPayments = await loadTossPayments(CLIENT_KEY);
  const payment = tossPayments.payment({ customerKey: 'ANONYMOUS' });

  // 토스 SDK의 requestPayment는 method별로 오버로드돼 있어 유니언 타입을 그대로 넘기면
  // 오버로드 매칭에 실패한다. method는 우리 쪽에서 TossMethod로 이미 좁혔으므로 안전하게 단언한다.
  await payment.requestPayment({
    method: params.method,
    amount: { currency: 'KRW', value: params.amount },
    orderId: `ORDER_${params.orderPk}`,
    orderName: params.orderName,
    successUrl: `${window.location.origin}/payment/success`,
    failUrl: `${window.location.origin}/payment/fail`,
  } as unknown as Parameters<typeof payment.requestPayment>[0]);
}
