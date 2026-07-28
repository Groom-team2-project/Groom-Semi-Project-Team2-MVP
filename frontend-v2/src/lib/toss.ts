import { loadTossPayments } from '@tosspayments/tosspayments-sdk';

const CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;

// successUrl 리다이렉트 후에도 결제 수단을 알 수 있게 세션에 보관
export const PAYMENT_METHOD_KEY = 'toss_payment_method';

export type TossMethod = 'CARD' | 'TRANSFER' | 'EASY_PAY';

/**
 * 결제 시도용 주문번호를 만든다: `ORDER_{주문PK}_{타임스탬프}`
 *
 * 토스는 orderId를 영구 유일값으로 취급해 한 번 쓴 값은 재사용할 수 없다(DUPLICATED_ORDER_ID).
 * 그래서 시도마다 타임스탬프를 붙여 새 값을 만들고, 백엔드는 `ORDER_{주문PK}_` 접두사로
 * 이 주문번호가 해당 주문의 것인지 검증한다.
 */
export function buildTossOrderId(orderPk: number | string) {
  return `ORDER_${orderPk}_${Date.now()}`;
}

// 토스 결제창 호출. 반환값은 이번 시도에 사용한 주문번호(승인 요청에 그대로 필요하다).
export async function requestTossPayment(params: {
  orderPk: number | string;
  amount: number;
  orderName: string;
  method: TossMethod;
}): Promise<string> {
  if (!CLIENT_KEY) {
    throw new Error('VITE_TOSS_CLIENT_KEY가 없습니다. frontend-v2/.env를 확인해주세요.');
  }
  sessionStorage.setItem(PAYMENT_METHOD_KEY, params.method);

  const tossOrderId = buildTossOrderId(params.orderPk);

  const toss = await loadTossPayments(CLIENT_KEY);
  const payment = toss.payment({ customerKey: 'ANONYMOUS' });

  await payment.requestPayment({
    method: params.method,
    amount: { currency: 'KRW', value: params.amount },
    orderId: tossOrderId,
    orderName: params.orderName,
    successUrl: `${window.location.origin}/payment/success`,
    failUrl: `${window.location.origin}/payment/fail`
  } as unknown as Parameters<typeof payment.requestPayment>[0]);

  return tossOrderId;
}
