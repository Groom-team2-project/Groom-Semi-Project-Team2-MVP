import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { confirmPayment } from '../api/payments';
import { ApiError } from '../api/client';
import { PAYMENT_METHOD_KEY } from '../lib/toss';
import { useToast } from '../components/Toast';

// 토스 성공 리다이렉트 → 백엔드 승인(confirm) 자동 호출
export function PaymentSuccessPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  const requested = useRef(false); // StrictMode 이중 실행 방지

  useEffect(() => {
    if (requested.current) return;
    requested.current = true;

    const paymentKey = params.get('paymentKey');
    const tossOrderId = params.get('orderId') ?? '';
    const orderPk = tossOrderId.replace('ORDER_', '');
    const method = sessionStorage.getItem(PAYMENT_METHOD_KEY) ?? 'CARD';

    if (!paymentKey || !orderPk) {
      setError('결제 정보가 올바르지 않아요.');
      return;
    }

    confirmPayment(orderPk, paymentKey, method)
      .then(() => {
        toast('결제가 완료되었어요! 📧 메일을 확인해보세요.');
        navigate(`/orders/${orderPk}`, { replace: true });
      })
      .catch((e) => {
        setError(
          e instanceof ApiError
            ? `${e.message} — 주문 상세에서 다시 시도할 수 있어요.`
            : '결제 승인에 실패했어요.'
        );
      });
  }, [params, navigate, toast]);

  const orderPk = (params.get('orderId') ?? '').replace('ORDER_', '');

  return (
    <div style={{ textAlign: 'center', paddingTop: 80 }} className="rise">
      {error ? (
        <>
          <span className="eyebrow">Payment Failed</span>
          <h1 className="h-display" style={{ fontSize: 32 }}>승인에 실패했어요</h1>
          <p className="text-muted" style={{ marginTop: 12 }}>{error}</p>
          {orderPk && (
            <Link to={`/orders/${orderPk}`} className="btn btn-primary" style={{ marginTop: 24 }}>
              주문으로 돌아가기
            </Link>
          )}
        </>
      ) : (
        <>
          <div className="spin" />
          <h1 className="h-section">결제 승인 중…</h1>
          <p className="text-muted" style={{ marginTop: 8 }}>토스 결제를 확인하고 있어요. 잠시만요.</p>
        </>
      )}
    </div>
  );
}
