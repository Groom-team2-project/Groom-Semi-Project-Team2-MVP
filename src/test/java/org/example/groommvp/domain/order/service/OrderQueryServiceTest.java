package org.example.groommvp.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDateTime;

import org.example.groommvp.domain.order.dto.OrderResponse;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderQueryServiceTest {

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("주문자 회원은 주문 상세를 조회할 수 있다")
    void getOrderByOwner() {
        Long memberId = 1L;
        LocalDateTime paymentExpiresAt = LocalDateTime.now().plusMinutes(30);

        Order order = orderRepository.save(
                Order.pendingPayment(memberId, 10000L, paymentExpiresAt)
        );
        ProductEntity product = productRepository.save(
                ProductEntity.builder()
                        .productName("Owner Product")
                        .productPrice(10_000)
                        .productImage("owner-product.png")
                        .build()
        );
        orderItemRepository.save(new OrderItem(order, product, 1, 10000));

        OrderResponse response = orderQueryService.getOrder(order.getId(), memberId);

        assertThat(response.paymentExpiresAt()).isEqualTo(paymentExpiresAt);
        assertThat(response.orderItems()).hasSize(1);
        assertThat(response.orderId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("다른 회원의 주문 상세를 조회하면 ORDER_FORBIDDEN 예외가 발생한다")
    void getOrderByOtherMemberThrowsForbidden() {
        Order order = orderRepository.save(Order.pendingPayment(1L, 10000L));

        assertThatThrownBy(() -> orderQueryService.getOrder(order.getId(), 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_FORBIDDEN);
    }

    @Test
    @DisplayName("회원 ID 없이 주문 상세를 조회하면 ORDER_FORBIDDEN 예외가 발생한다")
    void getOrderWithoutMemberIdThrowsForbidden() {
        Order order = orderRepository.save(Order.pendingPayment(1L, 10000L));

        assertThatThrownBy(() -> orderQueryService.getOrder(order.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_FORBIDDEN);
    }

    @Test
    @DisplayName("결제 대기 주문은 전달받은 결제 마감 시각을 저장한다")
    void pendingPaymentStoresPaymentExpiresAt() {
        LocalDateTime paymentExpiresAt =
                LocalDateTime.of(2026, 7, 29, 23, 0);

        Order order = orderRepository.save(
                Order.pendingPayment(1L, 10_000L, paymentExpiresAt)
        );

        assertThat(order.getPaymentExpiresAt()).isEqualTo(paymentExpiresAt);
    }
}
