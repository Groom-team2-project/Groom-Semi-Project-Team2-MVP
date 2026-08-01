package org.example.groommvp.domain.payment.client;

/**
 * 토스 결제 조회 응답의 {@code status} 값.
 *
 * <p>승인 요청 결과를 알 수 없게 된 주문(응답 타임아웃, 서버 재시작 등)을 정산할 때
 * "토스 쪽에서는 실제로 결제가 됐는가"를 판단하는 기준이 된다.
 */
public enum TossPaymentStatus {

	/** 결제 생성 초기 상태 */
	READY,
	/** 결제수단 인증 완료 (승인 전) */
	IN_PROGRESS,
	/** 가상계좌 입금 대기 중 */
	WAITING_FOR_DEPOSIT,
	/** 결제 승인됨 — 실제로 돈이 결제된 상태 */
	DONE,
	/** 결제 취소됨 */
	CANCELED,
	/** 부분 취소됨 */
	PARTIAL_CANCELED,
	/** 결제 승인 실패 */
	ABORTED,
	/** 유효 시간이 지나 거래가 취소된 상태 */
	EXPIRED;

	/** 결제가 성사되어 금액이 실제로 결제된 상태인지. */
	public boolean isPaid() {
		return this == DONE;
	}

	/** 이미 취소되어 환불이 불필요한 상태인지. */
	public boolean isCanceled() {
		return this == CANCELED || this == PARTIAL_CANCELED;
	}
}
