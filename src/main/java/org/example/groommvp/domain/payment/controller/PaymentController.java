package org.example.groommvp.domain.payment.controller;

import org.example.groommvp.domain.payment.dto.PaymentRequest;
import org.example.groommvp.domain.payment.dto.PaymentResponse;
import org.example.groommvp.domain.payment.service.PaymentService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping("/{orderId}/payments")
	public ResponseEntity<CommonResponse<PaymentResponse>> pay(
		@PathVariable Long orderId,
		@Valid @RequestBody PaymentRequest request
	) {
		PaymentResponse response = paymentService.pay(orderId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CommonResponse.success(response, "결제가 완료되었습니다."));
	}

}
