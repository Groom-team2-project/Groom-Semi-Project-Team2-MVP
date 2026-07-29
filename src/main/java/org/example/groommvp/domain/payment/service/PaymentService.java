package org.example.groommvp.domain.payment.service;

import java.util.List;
import java.util.Comparator;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.payment.dto.PaymentAttemptStarted;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final TossPaymentClient tossPaymentClient;
	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;
	private final PaymentAttemptService paymentAttemptService;

	/**
	 * 결제를 승인한다.
	 *
	 * <p><b>트랜잭션을 걸지 않는다.</b> 토스 승인은 외부 HTTP 호출이라 수 초가 걸리는데,
	 * 그 시간 내내 DB 트랜잭션과 락을 붙잡으면 커넥션 풀이 마른다. 대신 승인 전후의 DB 작업을
	 * {@link PaymentAttemptService} 의 독립 트랜잭션으로 나누어 호출한다.
	 *
	 * <pre>
	 * 1) begin()    주문을 PAYMENT_PROCESSING 으로 바꿔 커밋  → 만료 스케줄러가 건드리지 못함
	 * 2) confirm()  토스 승인 (트랜잭션·락 없이)
	 * 3) complete() 재고 확정 + 주문 완료 + 결제 저장
	 *    revert()   실패 시 결제 대기로 복귀 (예약 유지 → 재시도 가능)
	 * </pre>
	 */
	public PaymentResponse pay(Long orderId, PaymentRequest request) {
		// 1단계 — 승인 요청 전에 상태를 커밋한다. 실패하면 시도 자체가 시작되지 않는다.
		//         승인에 쓸 금액도 여기서 받는다 (서버가 보관한 주문 금액 = 위변조 방지).
		PaymentAttemptStarted started = paymentAttemptService.begin(orderId, request);

		// 2단계 — 트랜잭션 밖에서 토스를 호출한다.
		try {
			tossPaymentClient.confirm(request.paymentKey(), request.tossOrderId(), started.amount());
		} catch (RestClientException e) {
			// 승인 거절·사용자 취소 등. 주문은 살려두고 이 시도만 실패로 남긴다.
			log.error("토스 결제 승인 실패: orderId={}, {}", orderId, e.getMessage(), e);
			paymentAttemptService.revert(orderId, started.attemptId(), e.getMessage());
			throw new BusinessException(ErrorCode.PAYMENT_FAILED);
		}

		// 3단계 — 승인 성공을 반영한다.
		Payment payment = paymentAttemptService.complete(orderId, started.attemptId());
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
