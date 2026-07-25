package org.example.groommvp.domain.payment.event;

public record PaymentCompletedEvent (
	Long orderId,
	Long paymentId,
	Long amount
) {
}
