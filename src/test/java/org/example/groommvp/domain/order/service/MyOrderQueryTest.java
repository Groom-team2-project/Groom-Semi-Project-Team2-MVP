package org.example.groommvp.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.cart.service.CartOrderService;
import org.example.groommvp.domain.cart.service.CartService;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.order.dto.OrderResponse;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 마이페이지 주문 내역 조회 테스트.
 *
 * <p>회원 격리(남의 주문이 섞이지 않는지)와 최신순 정렬을 검증한다.
 */
@SpringBootTest
class MyOrderQueryTest {

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartOrderService cartOrderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Long memberId;
    private Long otherMemberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(
                MemberEntity.createKakaoMember("orders-me", "me@example.com", "나")).getMemberId();
        otherMemberId = memberRepository.save(
                MemberEntity.createKakaoMember("orders-other", "other@example.com", "남")).getMemberId();
        ProductEntity product = productRepository.save(ProductEntity.builder().productName("티셔츠").productPrice(10_000).productImage("test.png").build());
        productId = product.getProductId();
        stockRepository.save(new StockEntity(product, 1_000));
    }

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private Long placeOrder(Long buyerId, int quantity) {
        cartService.addItem(buyerId, new CartItemAddRequest(productId, quantity));
        return cartOrderService.checkout(buyerId).orderId();
    }

    @Test
    @DisplayName("주문이 없으면 빈 목록을 반환한다")
    void getMyOrders_returnsEmptyWhenNoOrders() {
        assertThat(orderQueryService.getMyOrders(memberId)).isEmpty();
    }

    @Test
    @DisplayName("내 주문만 최신순으로 반환하고 남의 주문은 섞이지 않는다 (회원 격리)")
    void getMyOrders_returnsOwnOrdersNewestFirst() {
        Long firstOrderId = placeOrder(memberId, 1);
        Long secondOrderId = placeOrder(memberId, 2);
        Long othersOrderId = placeOrder(otherMemberId, 1);

        List<OrderResponse> myOrders = orderQueryService.getMyOrders(memberId);

        assertThat(myOrders).extracting(OrderResponse::orderId)
                .containsExactly(secondOrderId, firstOrderId)
                .doesNotContain(othersOrderId);
    }

    @Test
    @DisplayName("주문 상품 정보(상품명·수량·금액)가 함께 담긴다")
    void getMyOrders_includesOrderItems() {
        placeOrder(memberId, 3);

        List<OrderResponse> myOrders = orderQueryService.getMyOrders(memberId);

        assertThat(myOrders).singleElement().satisfies(order -> {
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.totalPrice()).isEqualTo(30_000L);
            assertThat(order.orderItems()).singleElement().satisfies(item -> {
                assertThat(item.productName()).isEqualTo("티셔츠");
                assertThat(item.quantity()).isEqualTo(3);
                assertThat(item.itemTotalPrice()).isEqualTo(30_000L);
            });
        });
    }

    @Test
    @DisplayName("인증 정보가 없으면 조회할 수 없다")
    void getMyOrders_requiresMember() {
        assertThatThrownBy(() -> orderQueryService.getMyOrders(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }
}
