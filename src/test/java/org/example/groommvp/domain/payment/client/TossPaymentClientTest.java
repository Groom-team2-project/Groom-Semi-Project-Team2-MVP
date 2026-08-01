package org.example.groommvp.domain.payment.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 결제 조회 연동의 요청/응답 계약 검증.
 *
 * <p>정산 로직이 이 결과로 "완료 재시도 vs 자동 환불"을 판단하므로,
 * 조회가 없음(404)과 조회 실패(그 외 오류)를 반드시 구분해야 한다.
 */
class TossPaymentClientTest {

	private static final String SECRET_KEY = "test_sk_dummy";
	private static final String LOOKUP_URL = "https://api.tosspayments.example/v1/payments/orders/{orderId}";

	private TossPaymentClient client;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new TossPaymentClient(
			builder,
			SECRET_KEY,
			"https://api.tosspayments.example/v1/payments/confirm",
			"https://api.tosspayments.example/v1/payments/{paymentKey}/cancel",
			LOOKUP_URL
		);
	}

	@Test
	@DisplayName("결제가 승인된 주문을 조회하면 DONE 상태와 금액을 반환한다")
	void findByOrderId_paid() {
		String tossOrderId = "ORDER_42_1753622752458";
		String expectedAuth = "Basic " + Base64.getEncoder()
			.encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));

		server.expect(requestTo("https://api.tosspayments.example/v1/payments/orders/" + tossOrderId))
			.andExpect(method(org.springframework.http.HttpMethod.GET))
			.andExpect(header("Authorization", expectedAuth))
			.andRespond(withSuccess("""
				{
				  "paymentKey": "test_pk_abc",
				  "orderId": "%s",
				  "status": "DONE",
				  "totalAmount": 20000,
				  "method": "카드",
				  "approvedAt": "2026-07-29T10:00:00+09:00",
				  "card": { "issuerCode": "11" }
				}
				""".formatted(tossOrderId), MediaType.APPLICATION_JSON));

		Optional<TossPaymentLookupResponse> result = client.findByOrderId(tossOrderId);

		assertThat(result).isPresent();
		TossPaymentLookupResponse payment = result.orElseThrow();
		assertThat(payment.status()).isEqualTo(TossPaymentStatus.DONE);
		assertThat(payment.isPaid()).isTrue();
		assertThat(payment.totalAmount()).isEqualTo(20000L);
		assertThat(payment.paymentKey()).isEqualTo("test_pk_abc");
		assertThat(payment.matchesAmount(20000L)).isTrue();   // 서버 주문 금액과 일치
		assertThat(payment.matchesAmount(19000L)).isFalse();  // 금액 불일치 감지
		server.verify();
	}

	@Test
	@DisplayName("결제 이력이 없는 주문번호(404)는 예외가 아니라 빈 결과로 돌아온다")
	void findByOrderId_notFound() {
		server.expect(requestTo("https://api.tosspayments.example/v1/payments/orders/ORDER_99_1"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{"code":"NOT_FOUND_PAYMENT","message":"존재하지 않는 결제 정보 입니다."}
					"""));

		// 결제를 시작하지 않은 주문이라는 "정상적인 판단 근거"이므로 예외를 던지면 안 된다
		assertThat(client.findByOrderId("ORDER_99_1")).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("인증 실패 등 조회 자체가 실패하면 예외를 던진다 (결제 없음으로 오판하지 않는다)")
	void findByOrderId_unauthorized() {
		server.expect(requestTo("https://api.tosspayments.example/v1/payments/orders/ORDER_42_1"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
					{"code":"UNAUTHORIZED_KEY","message":"인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다."}
					"""));

		assertThatThrownBy(() -> client.findByOrderId("ORDER_42_1"))
			.isInstanceOf(org.springframework.web.client.RestClientException.class);
		server.verify();
	}

	@Test
	@DisplayName("취소된 결제는 isPaid가 false이고 isCanceled로 구분된다")
	void findByOrderId_canceled() {
		server.expect(requestTo("https://api.tosspayments.example/v1/payments/orders/ORDER_42_2"))
			.andRespond(withSuccess("""
				{
				  "paymentKey": "test_pk_abc",
				  "orderId": "ORDER_42_2",
				  "status": "CANCELED",
				  "totalAmount": 20000
				}
				""", MediaType.APPLICATION_JSON));

		TossPaymentLookupResponse payment = client.findByOrderId("ORDER_42_2").orElseThrow();

		assertThat(payment.isPaid()).isFalse();
		assertThat(payment.status().isCanceled()).isTrue();
		server.verify();
	}
}
