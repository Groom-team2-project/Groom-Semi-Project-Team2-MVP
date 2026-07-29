package org.example.groommvp.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 결제 조회 응답. (정산에 필요한 필드만 받는다)
 *
 * <p>토스 응답에는 카드/가상계좌 등 수단별 필드가 많지만, 우리가 판단에 쓰는 것은
 * "결제됐는가({@code status})", "얼마인가({@code totalAmount})", "어떤 결제인가
 * ({@code paymentKey})" 뿐이다. 나머지 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentLookupResponse(
	String paymentKey,
	String orderId,
	TossPaymentStatus status,
	Long totalAmount,
	String method,
	String approvedAt
) {

	public boolean isPaid() {
		return status != null && status.isPaid();
	}

	/** 서버가 보관한 주문 금액과 실제 결제 금액이 일치하는지. */
	public boolean matchesAmount(Long expectedAmount) {
		return totalAmount != null && totalAmount.equals(expectedAmount);
	}
}
