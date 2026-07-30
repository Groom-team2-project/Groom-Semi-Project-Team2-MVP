package org.example.groommvp.domain.payment.entity;

/**
 * 결제 시도의 진행 상태.
 *
 * <p>{@link PaymentStatus}(결제 자체의 상태)와 다르다. 한 주문에 결제는 하나지만
 * 시도는 여러 번 있을 수 있어(카드 거절 후 재시도 등) 시도마다 이 상태를 남긴다.
 */
public enum PaymentAttemptStatus {

	/** 토스에 승인 요청을 보냈고 결과를 아직 반영하지 못한 상태 — 정산 대상 */
	STARTED,
	/** 승인 성공 + 서버 반영까지 완료 */
	SUCCEEDED,
	/** 승인 거절·사용자 취소 등으로 이 시도는 실패 (주문은 살아 있어 재시도 가능) */
	FAILED,
	/** 정산으로도 결론을 내지 못해 운영 확인이 필요한 상태 */
	NEEDS_REVIEW;

	/** 결과가 반영되지 않아 정산이 필요한 상태인지. */
	public boolean isUnresolved() {
		return this == STARTED;
	}
}
