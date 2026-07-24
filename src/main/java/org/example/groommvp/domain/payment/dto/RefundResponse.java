package org.example.groommvp.domain.payment.dto;

import java.time.LocalDateTime;

import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.entity.PaymentStatus;

import lombok.Builder;

@Builder
public record RefundResponse(
	Long paymentId,
	Long orderId,
	Long amount,
	PaymentStatus status,
	LocalDateTime canceledAt
) {
	public static RefundResponse from(Payment payment) {
		return RefundResponse.builder()
			.paymentId(payment.getId())
			.orderId(payment.getOrder().getId())
			.amount(payment.getAmount())
			.status(payment.getStatus())
			.canceledAt(payment.getCanceledAt())
			.build();
	}
}
