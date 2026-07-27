import { Link, useSearchParams } from 'react-router-dom';

// 토스 결제창에서 실패/취소 시 리다이렉트되는 화면 — 주문은 PENDING 유지라 재시도 가능
export function PaymentFailPage() {
  const [params] = useSearchParams();
  // 주문번호 형식: ORDER_{주문PK}_{시도}
  const orderPk = (params.get('orderId') ?? '').split('_')[1] ?? '';

  return (
    <div style={{ textAlign: 'center', paddingTop: 80 }} className="rise">
      <span className="eyebrow">Payment Failed</span>
      <h1 className="h-display" style={{ fontSize: 32 }}>결제하지 못했어요</h1>
      <p className="text-muted" style={{ marginTop: 12 }}>
        {params.get('message') ?? '결제가 취소되었거나 실패했어요.'}
      </p>
      <div className="row" style={{ justifyContent: 'center', marginTop: 28 }}>
        {orderPk && (
          <Link to={`/orders/${orderPk}`} className="btn btn-primary">
            다시 결제하기
          </Link>
        )}
        <Link to="/" className="btn btn-ghost">쇼핑 계속하기</Link>
      </div>
    </div>
  );
}
