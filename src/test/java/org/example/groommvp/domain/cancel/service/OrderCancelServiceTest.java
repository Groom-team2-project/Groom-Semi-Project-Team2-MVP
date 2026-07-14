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
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
	@DisplayName("주문을 취소하면 상태가 CANCELED로 바뀌고 재고가 복구된다")
	void cancel_success() {
		// given
		Long orderId = 1L;
		ProductEntity product = product(10L, "티셔츠", 10000);
		Order order = order(orderId);                                    // COMPLETED 주문
		OrderItem orderItem = new OrderItem(order, product, 2, 10000);   // 2개 샀던 품목
		StockEntity stock = StockEntity.builder().product(product).stocks(8).build(); // 현재 재고 8

		given(orderRepository.findByIdWithPessimisticLock(orderId)).willReturn(Optional.of(order));
		given(orderItemRepository.findByOrder(order)).willReturn(List.of(orderItem));
		given(stockRepository.findByProductIdWithPessimisticLock(10L)).willReturn(Optional.of(stock));
		given(stockHistoryRepository.save(any(StockHistoryEntity.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		// when
		OrderCancelResponse response = orderCancelService.cancel(orderId);

		// then
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);  // 상태 변경됨
		assertThat(order.getCanceledAt()).isNotNull();                  // 취소 시각 기록됨
		assertThat(stock.getStocks()).isEqualTo(10);           // 8 + 2 = 재고 복구됨
		assertThat(response.restoredItems()).hasSize(1);
		assertThat(response.restoredItems().get(0).productId()).isEqualTo(10L);
		assertThat(response.restoredItems().get(0).quantity()).isEqualTo(2);
		verify(stockHistoryRepository).save(any(StockHistoryEntity.class)); // RESTORE 이력 저장됨
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
		Order order = order(orderId);
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

	private Order order(Long id) {
		Order order = new Order(20000);
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}
}
