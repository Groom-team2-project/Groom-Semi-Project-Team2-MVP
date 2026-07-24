package org.example.groommvp.domain.payment.service;

import java.util.List;
import java.util.Optional;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.client.TossPaymentClient;
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
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.domain.stock.entity.StockHistoryType;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

	@Mock private OrderRepository orderRepository;
	@Mock private OrderItemRepository orderItemRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private TossPaymentClient tossPaymentClient;   // 외부 호출은 Mock
	@Mock private StockRepository stockRepository;
	@Mock private StockHistoryRepository stockHistoryRepository;
	@InjectMocks private PaymentService paymentService;

	@Test
	@DisplayName("결제 성공 시 토스 승인 후 상태가 PAID가 된다")
	void pay_success() {
		// given
		Long orderId = 1L;
		Long productId = 10L;
		Order order = order(orderId, 20000L, OrderStatus.PENDING_PAYMENT);
		ProductEntity product = product(productId);
		OrderItem orderItem = new OrderItem(order, product, 1, 20000);
		StockEntity stock = StockEntity.builder()
			.product(product)
			.stocks(1)
			.build();
		stock.reserve(1);

		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(false);
		given(orderItemRepository.findByOrderIdWithProduct(orderId)).willReturn(List.of(orderItem));
		given(stockRepository.findByProductIdWithPessimisticLock(productId)).willReturn(Optional.of(stock));
		given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		PaymentResponse response = paymentService.pay(orderId, new PaymentRequest("test_pk_123", "CARD"));

		// then
		assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(response.paidAt()).isNotNull();

		verify(tossPaymentClient).confirm("test_pk_123", "ORDER_1", 20000L);  // 서버 금액으로 승인 요청했는지
		verify(paymentRepository).saveAndFlush(any(Payment.class));

		ArgumentCaptor<StockHistoryEntity> historyCaptor = ArgumentCaptor.forClass(StockHistoryEntity.class);

		assertThat(stock.getStocks()).isZero();
		assertThat(stock.getReservedStocks()).isZero();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

		verify(stockHistoryRepository).save(historyCaptor.capture());
		StockHistoryEntity history = historyCaptor.getValue();
		assertThat(history.getChangeType()).isEqualTo(StockHistoryType.CONFIRM);
		assertThat(history.getOrderId()).isEqualTo(orderId);
		assertThat(history.getChangedQty()).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 결제된 주문이면 토스 호출 없이 PAYMENT_ALREADY_EXISTS 예외가 발생한다")
	void pay_alreadyExists() {
		// given
		Long orderId = 1L;
		Order order = order(orderId, 20000L, OrderStatus.PENDING_PAYMENT);
		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(true);

		// when & then
		assertThatThrownBy(() -> paymentService.pay(orderId, new PaymentRequest("test_pk_123", "CARD")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);

		// 사전 체크에서 막혀 토스 승인은 호출되지 않아야 한다
		verify(tossPaymentClient, never()).confirm(any(), any(), anyLong());
	}

	@Test
	@DisplayName("토스 승인이 실패하면 PAYMENT_FAILED 예외가 발생하고 주문/재고는 그대로다")
	void pay_failed() {
		// given
		Long orderId = 1L;
		Order order = order(orderId, 20000L, OrderStatus.PENDING_PAYMENT);
		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(false);

		// 토스 승인 실패 시뮬레이션 (confirm이 예외를 던지도록)
		doThrow(new RestClientException("toss error"))
			.when(tossPaymentClient).confirm(anyString(), anyString(), anyLong());

		// when & then
		assertThatThrownBy(() -> paymentService.pay(orderId, new PaymentRequest("test_pk_123", "CARD")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_FAILED);

		// 실패 시 저장·재고확정·상태변경이 일어나지 않아야 한다 (롤백 → 재시도 가능)
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
		verify(paymentRepository, never()).saveAndFlush(any());
		verify(stockHistoryRepository, never()).save(any());
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
		org.springframework.test.util.ReflectionTestUtils.setField(o, "id", id);
		return o;
	}

	private ProductEntity product(Long id) {
		ProductEntity product = ProductEntity.builder()
			.productName("테스트 상품")
			.productPrice(20000)
			.build();
		org.springframework.test.util.ReflectionTestUtils.setField(product, "productId", id);
		return product;
	}
}
