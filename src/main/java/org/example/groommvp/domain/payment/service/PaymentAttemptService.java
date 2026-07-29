package org.example.groommvp.domain.payment.service;

import java.util.Comparator;
import java.util.List;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.dto.PaymentAttemptStarted;
import org.example.groommvp.domain.payment.dto.PaymentRequest;
import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.entity.PaymentAttempt;
import org.example.groommvp.domain.payment.event.PaymentCompletedEvent;
import org.example.groommvp.domain.payment.repository.PaymentAttemptRepository;
import org.example.groommvp.domain.payment.repository.PaymentRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 진행의 각 단계를 <b>독립 트랜잭션</b>으로 수행한다.
 *
 * <p><b>왜 분리하는가:</b> 토스 승인은 외부 HTTP 호출이라 수 초가 걸린다. 그동안 주문을
 * 보호하지 않으면 예약 만료 스케줄러가 주문을 취소해 "토스는 결제 성공, 주문은 취소"가 된다.
 * 그래서 승인 요청 <b>전에</b> 주문을 {@code PAYMENT_PROCESSING} 으로 바꿔 커밋해두어야 한다.
 *
 * <p>반대로 승인 호출 내내 DB 락과 커넥션을 붙잡으면 커넥션 풀이 마른다. 그래서
 * "상태 전이 커밋 → (트랜잭션 밖에서) 승인 호출 → 결과 반영 커밋" 세 단계로 쪼갠다.
 *
 * <p>같은 클래스 안에서 호출하면 스프링 프록시를 지나지 않아 트랜잭션이 분리되지 않는다.
 * 그래서 오케스트레이션({@link PaymentService})과 단계 실행(이 클래스)을 다른 빈으로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptService {

	private static final String CONFIRM_REASON = "PAYMENT_CONFIRM";

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentAttemptRepository paymentAttemptRepository;
	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 1단계: 결제 시도를 시작한다.
	 *
	 * <p>주문을 {@code PAYMENT_PROCESSING} 으로 바꾸고 시도 이력을 남긴 뒤 커밋한다.
	 * 커밋해야 만료 스케줄러가 이 주문을 건드리지 않는다.
	 *
	 * @return 시도 이력 ID와 서버가 보관한 주문 금액 (승인은 이 금액으로만 한다)
	 */
	@Transactional
	public PaymentAttemptStarted begin(Long orderId, PaymentRequest request) {
		Order order = orderRepository.findByIdWithPessimisticLock(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		if (paymentRepository.existsByOrder(order)) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
		}

		// 클라이언트가 보낸 주문번호가 이 주문의 것인지 확인 (다른 주문의 결제를 가로채지 못하게)
		if (!request.tossOrderId().startsWith("ORDER_" + orderId + "_")) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		// PENDING_PAYMENT 가 아니면 여기서 막힌다.
		// 이미 PAYMENT_PROCESSING 이면 중복 클릭이므로 두 번째 요청은 진행되지 않는다.
		order.startPayment();

		PaymentAttempt attempt = new PaymentAttempt(
			orderId, request.tossOrderId(), request.paymentKey(), order.getTotalPrice(), request.method());

		try {
			paymentAttemptRepository.saveAndFlush(attempt);
		} catch (DataIntegrityViolationException e) {
			// 같은 주문번호로 두 번 시도한 경우 (UNIQUE 충돌)
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
		}
		return new PaymentAttemptStarted(attempt.getId(), order.getTotalPrice());
	}

	/**
	 * 3단계(성공): 승인 결과를 반영한다.
	 *
	 * <p>예약 재고를 확정하고 주문을 완료 처리한 뒤 결제와 시도 이력을 저장한다.
	 * 결제 수단·결제 키는 시도 이력에서 읽어, 정산도 같은 경로로 완료 처리할 수 있게 한다.
	 */
	@Transactional
	public Payment complete(Long orderId, Long attemptId) {
		PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		Order order = orderRepository.findByIdWithPessimisticLock(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		List<OrderItem> orderItems = orderItemRepository.findByOrderIdWithProduct(orderId);
		confirmReservedStocks(order, orderItems);
		order.completePayment();

		Payment payment = new Payment(
			order, order.getTotalPrice(), attempt.getMethod(), attempt.getPaymentKey());
		payment.pay();

		try {
			paymentRepository.saveAndFlush(payment);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS);
		}

		attempt.succeed();

		eventPublisher.publishEvent(
			new PaymentCompletedEvent(orderId, payment.getId(), payment.getAmount()));
		return payment;
	}

	/**
	 * 정산: 토스에서 결제가 확인된 시도를 처리한다.
	 *
	 * <p>주문을 잠근 뒤 상태를 <b>다시</b> 확인해 결론을 낸다. 이 재확인이 없으면
	 * 이미 완료된 주문을 또 완료하거나(중복 완료), 살아 있는 주문을 환불해버릴 수 있다.
	 *
	 * <p>환불은 외부 호출이라 여기서 하지 않고 {@link PaymentReconcileAction#NEEDS_REFUND}
	 * 로 알린다. (트랜잭션 안에서 외부 호출을 하지 않는다)
	 */
	@Transactional
	public PaymentReconcileAction settleConfirmedPayment(Long attemptId) {
		PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		if (!attempt.getStatus().isUnresolved()) {
			return PaymentReconcileAction.SKIPPED;  // 그사이 다른 처리가 끝났다
		}

		Order order = orderRepository.findByIdWithPessimisticLock(attempt.getOrderId())
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		// 이미 완료된 주문이면 결제도 저장돼 있다. 중복 완료를 막고 시도만 성공으로 정리한다.
		if (order.getStatus().isCompleted()) {
			attempt.succeed();
			return PaymentReconcileAction.SKIPPED;
		}

		// 승인 중 상태가 아니면 만료·취소로 예약이 이미 풀렸다는 뜻이다.
		// 재고를 되돌릴 수 없으므로 받은 돈을 환불해야 한다.
		if (!order.getStatus().isPaymentProcessing()) {
			log.warn("결제는 성공했지만 주문이 이미 종료된 상태입니다. 환불이 필요합니다. "
				+ "orderId={}, status={}", order.getId(), order.getStatus());
			return PaymentReconcileAction.NEEDS_REFUND;
		}

		// 예약이 남아 있다 — 원래 하려던 완료 처리를 한 번만 재시도한다.
		complete(order.getId(), attemptId);
		return PaymentReconcileAction.COMPLETED;
	}

	/** 이 시도를 실패로 기록한다. (정산 결과 반영용) */
	@Transactional
	public void markFailed(Long attemptId, String reason) {
		markAttempt(attemptId, attempt -> attempt.fail(reason));
	}

	/** 결론을 내지 못해 운영 확인이 필요함을 기록한다. */
	@Transactional
	public void markNeedsReview(Long attemptId, String reason) {
		log.error("결제 시도에 운영 확인이 필요합니다. attemptId={}, reason={}", attemptId, reason);
		markAttempt(attemptId, attempt -> attempt.needsReview(reason));
	}

	/**
	 * 3단계(실패): 이 시도만 실패로 처리하고 주문은 결제 대기로 되돌린다.
	 *
	 * <p>카드 거절이나 사용자의 결제창 취소는 주문을 취소할 사유가 아니다. 예약을 유지해
	 * 만료 시각 전까지 다른 결제 수단으로 재시도할 수 있게 한다.
	 */
	@Transactional
	public void revert(Long orderId, Long attemptId, String reason) {
		Order order = orderRepository.findByIdWithPessimisticLock(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		// 정산이 먼저 처리했거나 만료된 경우 등 진행 중이 아니면 건드리지 않는다.
		if (!order.getStatus().isPaymentProcessing()) {
			log.warn("결제 대기로 되돌릴 수 없는 상태입니다. orderId={}, status={}", orderId, order.getStatus());
			return;
		}

		order.revertToPendingPayment();
		markAttempt(attemptId, attempt -> attempt.fail(reason));
	}

	private void markAttempt(Long attemptId, java.util.function.Consumer<PaymentAttempt> action) {
		paymentAttemptRepository.findById(attemptId).ifPresent(action);
	}

	/** 예약된 재고를 실제 차감으로 확정한다. 상품 ID 오름차순으로 잠가 교착을 피한다. */
	private void confirmReservedStocks(Order order, List<OrderItem> orderItems) {
		List<OrderItem> sorted = orderItems.stream()
			.sorted(Comparator.comparing(orderItem -> orderItem.getProduct().getProductId()))
			.toList();

		for (OrderItem orderItem : sorted) {
			Long productId = orderItem.getProduct().getProductId();
			int quantity = orderItem.getQuantity();

			StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

			stock.confirm(quantity);
			stockHistoryRepository.save(
				StockHistoryEntity.confirm(stock, order.getId(), quantity, CONFIRM_REASON));
		}
	}
}
