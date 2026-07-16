package org.example.groommvp.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(
	@NotBlank(message = "결제 수단은 필수입니다.")
	String method
) {
}
