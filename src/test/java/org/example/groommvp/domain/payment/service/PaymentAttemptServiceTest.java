package org.example.groommvp.domain.payment.service;

import java.util.List;
import java.util.Optional;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.dto.PaymentAttemptStarted;
import org.example.groommvp.domain.payment.dto.PaymentRequest;
import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.entity.PaymentAttempt;
import org.example.groommvp.domain.payment.entity.PaymentAttemptStatus;
import org.example.groommvp.domain.payment.entity.PaymentStatus;
import org.example.groommvp.domain.payment.event.PaymentCompletedEvent;
import org.example.groommvp.domain.payment.repository.PaymentAttemptRepository;
import org.example.groommvp.domain.payment.repository.PaymentRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryType;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 결제 단계별 상태 전이 검증.
 *
 * <p>핵심은 "승인 요청 전에 주문이 만료 대상에서 빠지는가"와 "실패가 주문을 취소하지 않는가"다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceTest {

	@Mock private OrderRepository orderRepository;
	@Mock private OrderItemRepository orderItemRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentAttemptRepository paymentAttemptRepository;
	@Mock private StockRepository stockRepository;
	@Mock private StockHistoryRepository stockHistoryRepository;
	@Mock private ApplicationEventPublisher eventPublisher;
	@InjectMocks private PaymentAttemptService paymentAttemptService;

	private static final Long ORDER_ID = 1L;
	private static final String TOSS_ORDER_ID = "ORDER_1_1700000000000";
	private static final PaymentRequest REQUEST =
		new PaymentRequest("test_pk_123", TOSS_ORDER_ID, "CARD");

	// ---------- begin ----------

	@Test
	@DisplayName("결제를 시작하면 주문이 PAYMENT_PROCESSING이 되고 시도 이력이 남는다")
	void begin_movesOrderToProcessing() {
		// given
		Order order = pendingOrder(20000L);
		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(false);
		given(paymentAttemptRepository.saveAndFlush(any(PaymentAttempt.class)))
			.willAnswer(inv -> {
				PaymentAttempt saved = inv.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", 99L);
				return saved;
			});

		// when
		PaymentAttemptStarted started = paymentAttemptService.begin(ORDER_ID, REQUEST);

		// then: 이 상태여야 만료 스케줄러가 주문을 건드리지 않는다
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
		assertThat(started.attemptId()).isEqualTo(99L);
		// 승인 금액은 클라이언트 값이 아니라 서버가 보관한 주문 금액이어야 한다
		assertThat(started.amount()).isEqualTo(20000L);

		ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
		verify(paymentAttemptRepository).saveAndFlush(captor.capture());
		PaymentAttempt attempt = captor.getValue();
		assertThat(attempt.getTossOrderId()).isEqualTo(TOSS_ORDER_ID);  // 정산 시 조회 키
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.STARTED);
	}

	@Test
	@DisplayName("이미 결제 진행 중인 주문은 다시 시작할 수 없다 (중복 클릭 방지)")
	void begin_alreadyProcessing() {
		// given: 첫 클릭으로 이미 PAYMENT_PROCESSING 이 된 주문
		Order order = pendingOrder(20000L);
		order.startPayment();
		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(false);

		// when & then
		assertThatThrownBy(() -> paymentAttemptService.begin(ORDER_ID, REQUEST))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_PENDING);

		verify(paymentAttemptRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("이미 결제된 주문은 시작 단계에서 막힌다")
	void begin_alreadyPaid() {
		Order order = pendingOrder(20000L);
		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(true);

		assertThatThrownBy(() -> paymentAttemptService.begin(ORDER_ID, REQUEST))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
	}

	@Test
	@DisplayName("다른 주문의 주문번호로 결제를 시작하면 거부된다")
	void begin_orderIdMismatch() {
		Order order = pendingOrder(20000L);
		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(false);

		PaymentRequest otherOrder = new PaymentRequest("test_pk_123", "ORDER_2_1700000000000", "CARD");

		assertThatThrownBy(() -> paymentAttemptService.begin(ORDER_ID, otherOrder))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
	}

	// ---------- complete ----------

	@Test
	@DisplayName("승인 성공을 반영하면 예약 재고가 확정되고 주문이 완료된다")
	void complete_confirmsStockAndOrder() {
		// given: 승인 요청을 보낸 상태(PAYMENT_PROCESSING), 재고 1개 예약됨
		Order order = pendingOrder(20000L);
		order.startPayment();
		ProductEntity product = product(10L);
		OrderItem orderItem = new OrderItem(order, product, 1, 20000);
		StockEntity stock = StockEntity.builder().product(product).stocks(1).build();
		stock.reserve(1);

		PaymentAttempt attempt = new PaymentAttempt(ORDER_ID, TOSS_ORDER_ID, "test_pk_123", 20000L, "CARD");

		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));
		given(orderItemRepository.findByOrderIdWithProduct(ORDER_ID)).willReturn(List.of(orderItem));
		given(stockRepository.findByProductIdWithPessimisticLock(10L)).willReturn(Optional.of(stock));
		given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));
		given(paymentAttemptRepository.findById(99L)).willReturn(Optional.of(attempt));

		// when
		Payment payment = paymentAttemptService.complete(ORDER_ID, 99L, REQUEST);

		// then
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
		assertThat(stock.getStocks()).isZero();          // 예약 → 실제 차감 확정
		assertThat(stock.getReservedStocks()).isZero();
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);

		ArgumentCaptor<StockHistoryEntity> captor = ArgumentCaptor.forClass(StockHistoryEntity.class);
		verify(stockHistoryRepository).save(captor.capture());
		assertThat(captor.getValue().getChangeType()).isEqualTo(StockHistoryType.CONFIRM);

		verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
	}

	// ---------- revert ----------

	@Test
	@DisplayName("승인 실패 시 주문을 취소하지 않고 결제 대기로 되돌려 재시도할 수 있게 한다")
	void revert_keepsOrderAlive() {
		// given
		Order order = pendingOrder(20000L);
		order.startPayment();
		PaymentAttempt attempt = new PaymentAttempt(ORDER_ID, TOSS_ORDER_ID, "test_pk_123", 20000L, "CARD");

		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));
		given(paymentAttemptRepository.findById(99L)).willReturn(Optional.of(attempt));

		// when
		paymentAttemptService.revert(ORDER_ID, 99L, "card declined");

		// then: 취소가 아니라 결제 대기로 복귀 — 예약이 유지되어 다른 수단으로 재시도 가능
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
		assertThat(order.getCanceledAt()).isNull();
		assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
		assertThat(attempt.getFailureReason()).isEqualTo("card declined");

		// 재고는 건드리지 않는다 (예약 유지)
		verify(stockHistoryRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 만료·취소된 주문은 되돌리지 않는다")
	void revert_skipsWhenNotProcessing() {
		// given: 정산이나 만료가 먼저 처리해 이미 취소된 주문
		Order order = pendingOrder(20000L);
		order.cancel();
		given(orderRepository.findByIdWithPessimisticLock(ORDER_ID)).willReturn(Optional.of(order));

		// when
		paymentAttemptService.revert(ORDER_ID, 99L, "card declined");

		// then: 상태를 건드리지 않고 조용히 넘어간다
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
		verify(paymentAttemptRepository, never()).findById(any());
	}

	private Order pendingOrder(Long totalPrice) {
		Order order = Order.pendingPayment(totalPrice);
		ReflectionTestUtils.setField(order, "id", ORDER_ID);
		return order;
	}

	private ProductEntity product(Long id) {
		ProductEntity product = ProductEntity.builder()
			.productName("테스트 상품")
			.productPrice(20000)
			.build();
		ReflectionTestUtils.setField(product, "productId", id);
		return product;
	}
}
