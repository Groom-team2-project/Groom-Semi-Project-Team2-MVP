import type { OrderStatus } from '../api/types';

const LABEL: Record<OrderStatus, { text: string; cls: string }> = {
  PENDING_PAYMENT: { text: '결제 대기', cls: 'pending' },
  PAYMENT_PROCESSING: { text: '결제 확인 중', cls: 'pending' },
  COMPLETED: { text: '결제 완료', cls: 'completed' },
  CANCELED: { text: '취소됨', cls: 'canceled' },
  PAYMENT_FAILED: { text: '결제 실패', cls: 'failed' }
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const meta = LABEL[status] ?? { text: status, cls: 'canceled' };
  return <span className={`badge ${meta.cls}`}>{meta.text}</span>;
}
