package org.example.groommvp.domain.payment.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TossPaymentClient {

	private final RestClient restClient;
	private final String secretKey;
	private final String confirmUrl;

	public TossPaymentClient(
		@Value("${toss.secret-key}") String secretKey,
		@Value("${toss.confirm-url}") String confirmUrl
	) {
		this.secretKey = secretKey;
		this.confirmUrl = confirmUrl;
		this.restClient = RestClient.create();
	}

	public void confirm(String paymentKey, String orderId, Long amount) {
		String encodedAuth = Base64.getEncoder()
			.encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

		restClient.post()
			.uri(confirmUrl)
			.header("Authorization", "Basic " + encodedAuth)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of(
				"paymentKey", paymentKey,
				"orderId", orderId,
				"amount", amount))
			.retrieve()
			.toBodilessEntity();

	}
}
