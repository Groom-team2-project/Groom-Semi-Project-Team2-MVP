package org.example.groommvp.domain.payment.entity;

import org.example.groommvp.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 승인 요청 한 번의 이력.
 *
 * <p><b>왜 필요한가:</b> 승인 요청을 보낸 뒤 응답을 받지 못하면(타임아웃, 서버 재시작)
 * "토스에서는 결제가 됐는지"를 나중에 확인해야 한다. 그러려면 승인에 사용한
 * {@code tossOrderId} 를 요청 전에 저장해둬야 한다.
 *
 * <p><b>왜 Payment 가 아니라 별도인가:</b> {@link Payment} 는 {@code order_id} 에 UNIQUE 제약이
 * 있어 주문당 한 건만 존재한다. 시도 시점에 Payment 를 만들면 카드 거절 후 재시도가
 * "이미 결제됨"으로 막힌다. 시도는 여러 번일 수 있으므로 이력을 따로 남긴다.
 *
 * <p>주문은 ID 로만 참조한다. 정산은 주문을 다시 잠그고 조회하므로 연관 객체가 필요하지 않다.
 */
@Entity
@Getter
@Table(name = "payment_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_attempt_id")
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	/** 토스 승인에 사용한 주문번호 ({@code ORDER_{주문PK}_{타임스탬프}}) — 결제 조회 키 */
	@Column(name = "toss_order_id", nullable = false, unique = true, length = 64)
	private String tossOrderId;

	@Column(name = "payment_key", length = 200)
	private String paymentKey;

	@Column(nullable = false)
	private Long amount;

	@Column(length = 20)
	private String method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentAttemptStatus status;

	/** 실패·보류 사유 (운영 확인용) */
	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	public PaymentAttempt(Long orderId, String tossOrderId, String paymentKey, Long amount, String method) {
		this.orderId = orderId;
		this.tossOrderId = tossOrderId;
		this.paymentKey = paymentKey;
		this.amount = amount;
		this.method = method;
		this.status = PaymentAttemptStatus.STARTED;
	}

	/** 승인 성공 + 서버 반영 완료. */
	public void succeed() {
		this.status = PaymentAttemptStatus.SUCCEEDED;
	}

	/** 이 시도만 실패 — 주문은 결제 대기로 돌아가 재시도할 수 있다. */
	public void fail(String reason) {
		this.status = PaymentAttemptStatus.FAILED;
		this.failureReason = truncate(reason);
	}

	/** 정산으로도 결론을 못 내 운영 확인이 필요하다. */
	public void needsReview(String reason) {
		this.status = PaymentAttemptStatus.NEEDS_REVIEW;
		this.failureReason = truncate(reason);
	}

	private String truncate(String reason) {
		if (reason == null) {
			return null;
		}
		return reason.length() <= 500 ? reason : reason.substring(0, 500);
	}
}
