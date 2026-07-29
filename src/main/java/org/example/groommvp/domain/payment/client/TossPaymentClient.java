package org.example.groommvp.domain.payment.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class TossPaymentClient {

	private final RestClient restClient;
	private final String secretKey;
	private final String confirmUrl;
	private final String cancelUrl;
	private final String lookupByOrderIdUrl;

	// RestClient 를 직접 생성하지 않고 주입받는다 — 테스트에서 MockRestServiceServer 로
	// 실제 요청/응답 계약을 검증할 수 있게 하기 위함이다.
	public TossPaymentClient(
		RestClient.Builder restClientBuilder,
		@Value("${toss.secret-key}") String secretKey,
		@Value("${toss.confirm-url}") String confirmUrl,
		@Value("${toss.cancel-url}") String cancelUrl,
		@Value("${toss.lookup-by-order-id-url}") String lookupByOrderIdUrl
	) {
		this.secretKey = secretKey;
		this.confirmUrl = confirmUrl;
		this.cancelUrl = cancelUrl;
		this.lookupByOrderIdUrl = lookupByOrderIdUrl;
		this.restClient = restClientBuilder.build();
	}

	public void confirm(String paymentKey, String orderId, Long amount) {
		restClient.post()
			.uri(confirmUrl)
			.header("Authorization", basicAuth())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of(
				"paymentKey", paymentKey,
				"orderId", orderId,
				"amount", amount))
			.retrieve()
			.toBodilessEntity();

	}

	public void cancel(String paymentKey, String cancelReason) {
		restClient.post()
			.uri(cancelUrl, paymentKey)
			.header("Authorization", basicAuth())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("cancelReason", cancelReason))
			.retrieve()
			.toBodilessEntity();
	}

	/**
	 * 주문번호로 토스 결제 상태를 조회한다.
	 *
	 * <p>승인 요청 결과를 알 수 없게 된 주문(응답 타임아웃, 처리 중 서버 종료 등)을 정산할 때 쓴다.
	 * "토스 쪽에서는 실제로 결제됐는가"를 확인해야 중복 완료나 중복 환불을 피할 수 있다.
	 *
	 * @param tossOrderId 승인 요청에 사용한 주문번호 ({@code ORDER_{주문PK}_{타임스탬프}})
	 * @return 결제 정보. 해당 주문번호로 결제를 시작한 적이 없으면(404) {@link Optional#empty()}
	 */
	public Optional<TossPaymentLookupResponse> findByOrderId(String tossOrderId) {
		return restClient.get()
			.uri(lookupByOrderIdUrl, tossOrderId)
			.header("Authorization", basicAuth())
			// 상태코드에 따라 분기해야 하므로 exchange 로 응답을 직접 다룬다.
			// retrieve().body() 를 쓰면 404 응답 본문(에러 객체)까지 결제 정보로 파싱해버린다.
			.exchange((request, response) -> {
				HttpStatusCode status = response.getStatusCode();

				// 결제를 시작하지 않은 주문번호는 404(NOT_FOUND_PAYMENT)다.
				// 오류가 아니라 "결제 이력 없음"이라는 정상적인 판단 근거이므로 빈 결과로 돌려준다.
				if (status.value() == 404) {
					return Optional.<TossPaymentLookupResponse>empty();
				}

				// 그 외 실패는 조회 자체가 안 된 것이다. 결제 없음으로 오판하면
				// 이미 결제된 주문을 취소해버릴 수 있으므로 예외로 올린다.
				if (status.isError()) {
					throw new HttpClientErrorException(status,
						"토스 결제 조회 실패: " + readBodySafely(response));
				}

				return Optional.ofNullable(response.bodyTo(TossPaymentLookupResponse.class));
			});
	}

	private String readBodySafely(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
		try {
			return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "(본문을 읽을 수 없음)";
		}
	}

	private String basicAuth() {
		String encoded = Base64.getEncoder()
			.encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
		return "Basic " + encoded;
	}
}
