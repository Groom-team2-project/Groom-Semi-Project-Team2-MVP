package org.example.groommvp.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.example.groommvp.domain.cancel.dto.OrderCancelResponse;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryType;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

	@Mock private OrderRepository orderRepository;
	@Mock private OrderItemRepository orderItemRepository;
	@Mock private StockRepository stockRepository;
	@Mock private StockHistoryRepository stockHistoryRepository;

	@InjectMocks private OrderCancelService orderCancelService;

	@Test
	@DisplayName("결제 대기 주문을 취소하면 CANCELED가 되고 예약 재고만 해제된다")
	void cancel_success() {
		// given: 재고 10개 중 2개가 예약된 결제 대기 주문
		Long orderId = 1L;
		ProductEntity product = product(10L, "티셔츠", 10000);
		Order order = pendingOrder(orderId);
		OrderItem orderItem = new OrderItem(order, product, 2, 10000);   // 2개 주문했던 품목
		StockEntity stock = StockEntity.builder().product(product).stocks(10).build();
		stock.reserve(2);

		given(orderRepository.findByIdWithPessimisticLock(orderId)).willReturn(Optional.of(order));
		given(orderItemRepository.findByOrder(order)).willReturn(List.of(orderItem));
		given(stockRepository.findByProductIdWithPessimisticLock(10L)).willReturn(Optional.of(stock));
		given(stockHistoryRepository.save(any(StockHistoryEntity.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		// when
		OrderCancelResponse response = orderCancelService.cancel(orderId);

		// then: 결제 전이라 실재고는 줄지 않았으므로 늘어나서도 안 된다 (재고 뻥튀기 방지)
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);  // 상태 변경됨
		assertThat(order.getCanceledAt()).isNotNull();                  // 취소 시각 기록됨
		assertThat(stock.getStocks()).isEqualTo(10);           // 실재고 유지
		assertThat(stock.getReservedStocks()).isZero();        // 예약만 해제
		assertThat(stock.getAvailableStocks()).isEqualTo(10);  // 다시 판매 가능
		assertThat(response.restoredItems()).hasSize(1);
		assertThat(response.restoredItems().get(0).productId()).isEqualTo(10L);
		assertThat(response.restoredItems().get(0).quantity()).isEqualTo(2);

		ArgumentCaptor<StockHistoryEntity> captor = ArgumentCaptor.forClass(StockHistoryEntity.class);
		verify(stockHistoryRepository).save(captor.capture());
		assertThat(captor.getValue().getChangeType()).isEqualTo(StockHistoryType.RELEASE);
	}

	@Test
	@DisplayName("결제 완료 주문은 취소 API로 취소할 수 없고 환불로 안내된다")
	void cancel_completedOrder_requiresRefund() {
		// given: 결제까지 끝난 주문 (여기서 취소되면 돈은 안 돌려주고 재고만 복구되는 문제)
		Long orderId = 1L;
		Order order = order(orderId);   // COMPLETED 주문
		given(orderRepository.findByIdWithPessimisticLock(orderId)).willReturn(Optional.of(order));

		// when & then
		assertThatThrownBy(() -> orderCancelService.cancel(orderId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.ORDER_REFUND_REQUIRED);

		// 주문 상태도, 재고도 건드리지 않아야 한다
		assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
		verify(stockHistoryRepository, never()).save(any());
	}

	@Test
	@DisplayName("존재하지 않는 주문을 취소하면 ORDER_NOT_FOUND 예외가 발생한다")
	void cancel_orderNotFound() {
		// given
		Long orderId = 999L;
		given(orderRepository.findByIdWithPessimisticLock(orderId)).willReturn(Optional.empty());

		// when & then: 예외 타입 + 에러코드 검증
		assertThatThrownBy(() -> orderCancelService.cancel(orderId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.ORDER_NOT_FOUND);

		// 주문이 없으니 재고 이력 저장은 절대 일어나면 안 됨
		verify(stockHistoryRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 취소된 주문을 다시 취소하면 ORDER_ALREADY_CANCELED 예외가 발생한다")
	void cancel_alreadyCanceled() {
		// given
		Long orderId = 1L;
		Order order = pendingOrder(orderId);
		order.cancel(); // 첫 취소 → CANCELED 상태가 됨
		given(orderRepository.findByIdWithPessimisticLock(orderId)).willReturn(Optional.of(order));

		// when & then
		assertThatThrownBy(() -> orderCancelService.cancel(orderId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.ORDER_ALREADY_CANCELED);

		// 중복 취소가 막혔으니 재고 복구 이력도 저장되면 안 됨 (재고 뻥튀기 방지)
		verify(stockHistoryRepository, never()).save(any());
	}

	private ProductEntity product(Long id, String name, int price) {
		ProductEntity product = ProductEntity.builder().productName(name).productPrice(price).build();
		ReflectionTestUtils.setField(product, "productId", id);
		return product;
	}

	/** 결제 완료(COMPLETED) 주문 */
	private Order order(Long id) {
		Order order = new Order(20000L);
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}

	/** 결제 대기(PENDING_PAYMENT) 주문 — 취소 API가 다루는 정상 대상 */
	private Order pendingOrder(Long id) {
		Order order = Order.pendingPayment(20000L);
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}
}
