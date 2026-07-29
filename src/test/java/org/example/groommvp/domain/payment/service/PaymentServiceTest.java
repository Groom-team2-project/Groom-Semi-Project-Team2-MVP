package org.example.groommvp.domain.payment.service;

import java.util.List;
import java.util.Optional;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.client.TossPaymentClient;
import org.example.groommvp.domain.payment.dto.PaymentAttemptStarted;
import org.example.groommvp.domain.payment.dto.PaymentRequest;
import org.example.groommvp.domain.payment.dto.PaymentResponse;
import org.example.groommvp.domain.payment.dto.RefundRequest;
import org.example.groommvp.domain.payment.dto.RefundResponse;
import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.entity.PaymentStatus;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 결제 오케스트레이션 검증.
 *
 * <p>단계별 DB 작업은 {@link PaymentAttemptService} 가 독립 트랜잭션으로 수행하므로 Mock 으로 두고,
 * 여기서는 "어떤 순서로 무엇을 호출하는가"를 검증한다. 특히 <b>토스 승인 전에 상태 전이가
 * 커밋되는 순서</b>가 지켜지는지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock private OrderRepository orderRepository;
	@Mock private OrderItemRepository orderItemRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private TossPaymentClient tossPaymentClient;   // 외부 호출은 Mock
	@Mock private StockRepository stockRepository;
	@Mock private StockHistoryRepository stockHistoryRepository;
	@Mock private PaymentAttemptService paymentAttemptService;
	@InjectMocks private PaymentService paymentService;

	private static final String TOSS_ORDER_ID = "ORDER_1_1700000000000";

	@Test
	@DisplayName("결제 성공 시 '상태 전이 → 토스 승인 → 결과 반영' 순서로 진행된다")
	void pay_success() {
		// given
		Long orderId = 1L;
		Order order = order(orderId, 20000L, OrderStatus.COMPLETED);
		Payment payment = new Payment(order, 20000L, "CARD", "test_pk_123");
		payment.pay();
		PaymentRequest request = new PaymentRequest("test_pk_123", TOSS_ORDER_ID, "CARD");

		// begin 이 서버가 보관한 주문 금액을 돌려준다
		given(paymentAttemptService.begin(orderId, request))
			.willReturn(new PaymentAttemptStarted(99L, 20000L));
		given(paymentAttemptService.complete(orderId, 99L, request)).willReturn(payment);

		// when
		PaymentResponse response = paymentService.pay(orderId, request);

		// then
		assertThat(response.status()).isEqualTo(PaymentStatus.PAID);

		// 토스 승인 전에 상태 전이가 끝나야 한다 (그래야 만료 스케줄러가 건드리지 못한다)
		InOrder order1 = inOrder(paymentAttemptService, tossPaymentClient);
		order1.verify(paymentAttemptService).begin(orderId, request);
		order1.verify(tossPaymentClient).confirm("test_pk_123", TOSS_ORDER_ID, 20000L);
		order1.verify(paymentAttemptService).complete(orderId, 99L, request);

		verify(paymentAttemptService, never()).revert(anyLong(), anyLong(), anyString());
	}

	@Test
	@DisplayName("토스 승인이 실패하면 주문을 결제 대기로 되돌리고 PAYMENT_FAILED를 던진다")
	void pay_failed_revertsToPending() {
		// given
		Long orderId = 1L;
		PaymentRequest request = new PaymentRequest("test_pk_123", TOSS_ORDER_ID, "CARD");
		given(paymentAttemptService.begin(orderId, request))
			.willReturn(new PaymentAttemptStarted(99L, 20000L));
		doThrow(new RestClientException("card declined"))
			.when(tossPaymentClient).confirm(anyString(), anyString(), anyLong());

		// when & then
		assertThatThrownBy(() -> paymentService.pay(orderId, request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

		// 주문을 취소하지 않고 결제 대기로 되돌려 재시도할 수 있게 한다
		verify(paymentAttemptService).revert(eq(orderId), eq(99L), anyString());
		verify(paymentAttemptService, never()).complete(anyLong(), anyLong(), any());
	}

	@Test
	@DisplayName("시작 단계에서 막히면 토스 승인은 호출되지 않는다")
	void pay_beginRejected_doesNotCallToss() {
		// given: 이미 결제된 주문이거나 중복 클릭 등으로 begin 이 거부하는 상황
		Long orderId = 1L;
		PaymentRequest request = new PaymentRequest("test_pk_123", TOSS_ORDER_ID, "CARD");
		given(paymentAttemptService.begin(orderId, request))
			.willThrow(new BusinessException(ErrorCode.PAYMENT_ALREADY_EXISTS));

		// when & then
		assertThatThrownBy(() -> paymentService.pay(orderId, request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);

		verify(tossPaymentClient, never()).confirm(any(), any(), anyLong());
		verify(paymentAttemptService, never()).complete(anyLong(), anyLong(), any());
	}

	@Test
	@DisplayName("환불 성공 시 토스 취소 후 상태가 REFUNDED가 되고 재고가 복구된다")
	void refund_success() {
		// given
		Long orderId = 1L;
		Long productId = 10L;
		Order order = order(orderId, 20000L, OrderStatus.COMPLETED);
		ProductEntity product = product(productId);
		OrderItem orderItem = new OrderItem(order, product, 1, 20000);
		StockEntity stock = StockEntity.builder()
			.product(product)
			.stocks(9)   // 1개 팔려서 실재고 9인 상태
			.build();

		Payment payment = new Payment(order, 20000L, "CARD", "test_pk_123");
		payment.pay();  // PAID 상태로 만듦

		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.findByOrder(order)).willReturn(Optional.of(payment));
		given(orderItemRepository.findByOrderIdWithProduct(orderId)).willReturn(List.of(orderItem));
		given(stockRepository.findByProductIdWithPessimisticLock(productId)).willReturn(Optional.of(stock));

		// when
		RefundResponse response = paymentService.refund(orderId, new RefundRequest("고객 변심"));

		// then
		assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock.getStocks()).isEqualTo(10);  // 9 + 1 복구

		verify(tossPaymentClient).cancel("test_pk_123", "고객 변심");

		ArgumentCaptor<StockHistoryEntity> historyCaptor = ArgumentCaptor.forClass(StockHistoryEntity.class);
		verify(stockHistoryRepository).save(historyCaptor.capture());
		assertThat(historyCaptor.getValue().getChangeType()).isEqualTo(StockHistoryType.RESTORE);
	}

	@Test
	@DisplayName("이미 환불된 결제는 토스 호출 없이 PAYMENT_NOT_REFUNDABLE 예외가 발생한다")
	void refund_notRefundable() {
		// given
		Long orderId = 1L;
		Order order = order(orderId, 20000L, OrderStatus.COMPLETED);
		Payment payment = new Payment(order, 20000L, "CARD", "test_pk_123");
		payment.pay();
		payment.refund();  // 이미 REFUNDED로 만들어 둠

		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.findByOrder(order)).willReturn(Optional.of(payment));

		// when & then
		assertThatThrownBy(() -> paymentService.refund(orderId, new RefundRequest("중복 환불")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);

		// 검증에서 막혀 토스 취소는 호출되면 안 된다
		verify(tossPaymentClient, never()).cancel(any(), any());
	}

	private Order order(Long id, Long totalPrice, OrderStatus status) {
		Order o = status == OrderStatus.PENDING_PAYMENT
			? Order.pendingPayment(totalPrice)
			: new Order(totalPrice);
		ReflectionTestUtils.setField(o, "id", id);
		return o;
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
