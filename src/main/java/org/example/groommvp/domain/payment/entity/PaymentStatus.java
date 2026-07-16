package org.example.groommvp.domain.payment.entity;

public enum PaymentStatus {
	PENDING,    // 결제 대기 (결제 생성됨, 승인 전)
	PAID,       // 결제 완료
	FAILED,     // 결제 실패
	CANCELED,   // 결제 취소 (승인 전 취소)
	REFUNDED;   // 환불 완료 (승인 후 되돌림)

	// 환불 가능 여부 — 결제 완료(PAID) 상태만 환불할 수 있음.
	public boolean isRefundable() {
		return this == PAID;
	}

	// 이미 최종 상태인지 (더 이상 전이 불가)
	public boolean isFinished() {
		return this == FAILED || this == CANCELED || this == REFUNDED;
	}
}
