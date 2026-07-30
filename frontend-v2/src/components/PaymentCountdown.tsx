import { useEffect, useState } from 'react';

function remainingMs(expiresAt: string) {
  return new Date(expiresAt).getTime() - Date.now();
}

function format(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/**
 * 결제 마감까지 남은 시간을 보여준다.
 *
 * 서버가 이 시각을 넘긴 주문의 예약 재고를 회수하고 주문을 취소한다. 사용자가 이유를 모르고
 * 취소당하지 않도록 남은 시간을 노출하고, 만료되는 순간 화면을 갱신한다.
 */
export function PaymentCountdown({
  expiresAt,
  onExpire
}: {
  expiresAt: string;
  onExpire?: () => void;
}) {
  const [left, setLeft] = useState(() => remainingMs(expiresAt));

  useEffect(() => {
    setLeft(remainingMs(expiresAt));

    const timer = setInterval(() => {
      const next = remainingMs(expiresAt);
      setLeft(next);
      if (next <= 0) {
        clearInterval(timer);
        onExpire?.();
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [expiresAt, onExpire]);

  if (left <= 0) {
    return <span className="countdown expired">결제 시간이 만료되었습니다</span>;
  }

  // 1분 미만이면 강조해 서둘러야 함을 알린다
  const urgent = left < 60_000;
  return (
    <span className={`countdown ${urgent ? 'urgent' : ''}`}>
      결제 마감까지 <b>{format(left)}</b>
    </span>
  );
}
