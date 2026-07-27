package org.example.groommvp.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
	@NotBlank(message = "결제 키는 필수입니다.")
	String paymentKey,

	/**
	 * 토스에 결제를 요청할 때 사용한 주문번호. (예: {@code ORDER_42_1753622752458})
	 *
	 * <p>토스는 orderId 를 영구 유일값으로 취급해 한 번 사용한 값은 재사용할 수 없다.
	 * 따라서 결제 재시도가 가능하도록 클라이언트가 시도마다 새 주문번호를 만들어 보내고,
	 * 서버는 승인 요청에 그대로 사용한다. 주문 식별은 경로 변수로 하며, 이 값이 해당
	 * 주문의 것인지는 서버가 접두사로 검증한다.
	 */
	@NotBlank(message = "주문번호는 필수입니다.")
	@Size(min = 6, max = 64, message = "주문번호는 6자 이상 64자 이하여야 합니다.")
	String tossOrderId,

	@NotBlank(message = "결제 수단은 필수입니다.")
	@Size(max = 20, message = "결제 수단은 20자 이하여야 합니다.")
	String method
) {
}
