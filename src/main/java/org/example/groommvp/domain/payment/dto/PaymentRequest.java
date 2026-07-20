package org.example.groommvp.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
	@NotBlank(message = "결제 키는 필수입니다.")
	String paymentKey,
	@NotBlank(message = "결제 수단은 필수입니다.")
	@Size(max = 20, message = "결제 수단은 20자 이하여야 합니다.")
	String method
) {
}
