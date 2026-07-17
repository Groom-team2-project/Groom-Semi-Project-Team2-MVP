package org.example.groommvp.domain.payment.dto;

import java.time.LocalDateTime;

import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.entity.PaymentStatus;

import lombok.Builder;

@Builder
public record PaymentResponse(
	Long paymentId,
	Long orderId,
	int amount,
	PaymentStatus status,
	LocalDateTime paidAt
) {
	public static PaymentResponse from(Payment payment) {
		return PaymentResponse.builder()
			.paymentId(payment.getId())
			.orderId(payment.getOrder().getId())
			.amount(payment.getAmount())
			.status(payment.getStatus())
			.paidAt(payment.getPaidAt())
			.build();
	}
}
