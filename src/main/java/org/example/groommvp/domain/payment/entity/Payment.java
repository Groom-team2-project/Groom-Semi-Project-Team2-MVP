package org.example.groommvp.domain.payment.entity;

import java.time.LocalDateTime;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_id")
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private Order order;

	@Column(nullable = false)
	private int amount;                 // 결제 금액

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Column(length = 20)
	private String method;              // 결제 수단 (예: CARD)

	private LocalDateTime paidAt;       // 결제 완료 시각
	private LocalDateTime canceledAt;   // 환불/취소 시각

	// 결제 생성 시 PENDING 상태로 시작
	public Payment(Order order, int amount, String method) {
		this.order = order;
		this.amount = amount;
		this.method = method;
		this.status = PaymentStatus.PENDING;
	}

	// 결제 승인: PENDING → PAID
	public void pay() {
		this.status = PaymentStatus.PAID;
		this.paidAt = LocalDateTime.now();
	}

	// 결제 실패: → FAILED
	public void fail() {
		this.status = PaymentStatus.FAILED;
	}
}
