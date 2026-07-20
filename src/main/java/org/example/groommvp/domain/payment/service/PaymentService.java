package org.example.groommvp.domain.payment.service;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.client.TossPaymentClient;
import org.example.groommvp.domain.payment.dto.PaymentRequest;
import org.example.groommvp.domain.payment.dto.PaymentResponse;
import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.repository.PaymentRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final TossPaymentClient tossPaymentClient;

	@Transactional
	public PaymentResponse pay(Long orderId, PaymentRequest request) {
		// 주문 조회
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		// 이중 결제 방지
		if (paymentRepository.existsByOrder(order)) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
		}

		// 토스 결제 승인
		tossPaymentClient.confirm(request.paymentKey(), String.valueOf(orderId), order.getTotalPrice());

		// 결제 생성 (PENDING)
		Payment payment = new Payment(order, order.getTotalPrice(), request.method());

		// (모의) 결제 승인 (PENDING -> PAID)
		payment.pay();

		// 저장 — 레이스 대비: UNIQUE 충돌을 409로 매핑
		try {
			paymentRepository.saveAndFlush(payment);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
		}
		return PaymentResponse.from(payment);
	}
}
