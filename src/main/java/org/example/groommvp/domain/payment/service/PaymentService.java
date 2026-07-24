package org.example.groommvp.domain.payment.service;

import java.util.List;
import java.util.Comparator;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.payment.dto.RefundRequest;
import org.example.groommvp.domain.payment.dto.RefundResponse;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
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
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final TossPaymentClient tossPaymentClient;
	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;

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
		try {
			tossPaymentClient.confirm(request.paymentKey(), "ORDER_" + orderId, order.getTotalPrice());
		} catch (RestClientException e) {
			// 토스가 돌려준 실패 원인(상태코드 + 응답 본문)을 로그로 남긴다
			log.error("토스 결제 승인 실패: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.PAYMENT_FAILED);
		}

		List<OrderItem> orderItems = orderItemRepository.findByOrderIdWithProduct(orderId);
		confirmReservedStocks(order, orderItems);
		order.completePayment();

		// 결제 생성 (PENDING)
		Payment payment = new Payment(order, order.getTotalPrice(), request.method(), request.paymentKey());

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

	@Transactional
	public RefundResponse refund(Long orderId, RefundRequest request) {
		// 1. 주문·결제 조회
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findByOrder(order)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		// 2. 환불 가능 검증 (토스 호출 전에 방어)
		if (!payment.getStatus().isRefundable()) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
		}

		// 3. 토스 결제 취소
		tossPaymentClient.cancel(payment.getPaymentKey(), request.cancelReason());

		// 4. 재고 복구 (실재고 increase + RESTORE 이력)
		List<OrderItem> orderItems = orderItemRepository.findByOrderIdWithProduct(orderId);
		restoreStocks(order, orderItems);

		// 5. 결제 환불 (PAID → REFUNDED)
		payment.refund();

		// 6. 주문 취소 (COMPLETED → CANCELED)
		order.cancel();

		return RefundResponse.from(payment);
	}

	private void confirmReservedStocks(Order order, List<OrderItem> orderItems) {
		List<OrderItem> sortedOrderItems = orderItems.stream()
				.sorted(Comparator.comparing(orderItem -> orderItem.getProduct().getProductId()))
				.toList();

		for (OrderItem orderItem : sortedOrderItems) {
			Long productId = orderItem.getProduct().getProductId();
			int quantity = orderItem.getQuantity();

			StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
					.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

			stock.confirm(quantity);
			stockHistoryRepository.save(
					StockHistoryEntity.confirm(stock, order.getId(), quantity, "PAYMENT_CONFIRM")
			);
		}
	}

	private void restoreStocks(Order order, List<OrderItem> orderItems) {
		List<OrderItem> sortedOrderItems = orderItems.stream()
			.sorted(Comparator.comparing(orderItem -> orderItem.getProduct().getProductId()))
			.toList();

		for (OrderItem orderItem : sortedOrderItems) {
			Long productId = orderItem.getProduct().getProductId();
			int quantity = orderItem.getQuantity();

			StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

			stock.increase(quantity);
			stockHistoryRepository.save(
				StockHistoryEntity.restore(stock, order.getId(), quantity, "PAYMENT_REFUND")
			);
		}
	}
}
