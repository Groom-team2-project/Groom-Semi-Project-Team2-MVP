package org.example.groommvp.domain.payment.service;

/**
 * 토스에서 결제가 성공한 것으로 확인된 시도에 대해 정산이 내린 결론.
 *
 * <p>판단은 주문을 잠근 상태에서 하고, 외부 호출(환불)은 트랜잭션 밖에서 해야 하므로
 * 결정과 실행을 이 값으로 분리한다.
 */
public enum PaymentReconcileAction {

	/** 예약이 남아 있어 주문을 완료 처리했다. 추가 조치 없음. */
	COMPLETED,

	/** 이미 만료·취소되어 되돌릴 수 없다. 받은 돈을 환불해야 한다. */
	NEEDS_REFUND,

	/** 다른 처리가 이미 끝나 손댈 것이 없다. */
	SKIPPED
}
