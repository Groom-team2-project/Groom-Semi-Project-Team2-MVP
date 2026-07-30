package org.example.groommvp.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.example.groommvp.domain.cart.dto.CartCheckoutResponse;
import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.order.entity.Order;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 미결제 주문의 예약 재고 회수 테스트.
 *
 * <p>결제창을 닫아버린 주문의 예약이 영구히 재고를 잠식하지 않는지, 그리고 회수가 <b>예약분만</b>
 * 되돌리는지(실물 재고를 부풀리지 않는지) 검증한다.
 */
@SpringBootTest
class ReservationExpiryServiceTest {

    private static final int INITIAL_STOCK = 100;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartOrderService cartOrderService;

    @Autowired
    private ReservationExpiryService reservationExpiryService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

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
    private MemberRepository memberRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long memberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(
                MemberEntity.createKakaoMember("expiry", "ex@example.com", "회원")).getMemberId();
        ProductEntity product = productRepository.save(ProductEntity.builder().productName("티셔츠").productPrice(10_000).productImage("test.png").build());
        productId = product.getProductId();
        stockRepository.save(new StockEntity(product, INITIAL_STOCK));
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

    private Long checkoutWithQuantity(int quantity) {
        cartService.addItem(memberId, new CartItemAddRequest(productId, quantity));
        CartCheckoutResponse response = cartOrderService.checkout(memberId);
        return response.orderId();
    }

    private StockEntity reloadStock() {
        return stockRepository.findAll().stream()
                .filter(stock -> stock.getProduct().getProductId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 오래된 주문을 흉내내기 위해 생성 시각을 과거로 되돌린다.
     *
     * <p>{@code created_at} 은 {@code @Column(updatable = false)} 라 엔티티를 고쳐 저장해도
     * UPDATE 문에 포함되지 않는다. (감사 필드라 의도된 동작) 그래서 네이티브 쿼리로 직접 바꾼다.
     */
    private void ageOrder(Long orderId, LocalDateTime createdAt) {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "update orders set created_at = :createdAt where order_id = :orderId")
                        .setParameter("createdAt", createdAt)
                        .setParameter("orderId", orderId)
                        .executeUpdate());
    }

    @Test
    @DisplayName("미결제로 방치된 주문의 예약 재고가 회수되고 주문은 취소된다")
    void releaseReservation_restoresAvailableStockAndCancelsOrder() {
        Long orderId = checkoutWithQuantity(3);
        assertThat(reloadStock().getAvailableStocks()).isEqualTo(INITIAL_STOCK - 3);

        boolean released = reservationExpiryService.releaseReservation(orderId);

        assertThat(released).isTrue();
        StockEntity stock = reloadStock();
        // 예약이 풀려 가용 재고가 원상 복구된다.
        assertThat(stock.getAvailableStocks()).isEqualTo(INITIAL_STOCK);
        // 실물 재고는 건드리지 않는다. (increase() 를 쓰면 여기가 103 이 되어 재고가 부풀려진다)
        assertThat(stock.getStocks()).isEqualTo(INITIAL_STOCK);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("결제가 끝난 주문은 회수 대상이 아니다 (만료 처리와 결제 완료의 경합)")
    void releaseReservation_skipsPaidOrder() {
        Long orderId = checkoutWithQuantity(3);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.startPayment();      // 승인 요청 → PAYMENT_PROCESSING
        order.completePayment();   // 승인 성공 → COMPLETED
        orderRepository.saveAndFlush(order);

        boolean released = reservationExpiryService.releaseReservation(orderId);

        assertThat(released).isFalse();
        // 결제된 주문의 예약은 그대로 남아야 한다.
        assertThat(reloadStock().getAvailableStocks()).isEqualTo(INITIAL_STOCK - 3);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("승인 요청 중인 주문(PAYMENT_PROCESSING)은 만료 대상에서 제외된다")
    void releaseReservation_skipsPaymentProcessingOrder() {
        // given: 사용자가 결제 버튼을 눌러 토스 승인 요청이 나간 상태
        Long orderId = checkoutWithQuantity(3);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.startPayment();
        orderRepository.saveAndFlush(order);
        // 만료 시각이 지나도록 오래된 주문으로 만든다
        ageOrder(orderId, LocalDateTime.now().minusHours(2));

        // when: 그사이 만료 스케줄러가 돌았다
        List<Long> expired = reservationExpiryService.findExpiredOrderIds(
                LocalDateTime.now().minusMinutes(30), 100);
        boolean released = reservationExpiryService.releaseReservation(orderId);

        // then: 승인 중인 주문은 조회 대상도 아니고, 직접 호출해도 회수되지 않아야 한다.
        //       그래야 "토스는 결제 성공, 주문은 취소"가 되는 상황을 막을 수 있다.
        assertThat(expired).doesNotContain(orderId);
        assertThat(released).isFalse();
        assertThat(reloadStock().getAvailableStocks()).isEqualTo(INITIAL_STOCK - 3);  // 예약 유지
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_PROCESSING);
    }

    @Test
    @DisplayName("이미 회수된 주문을 다시 회수해도 재고가 두 번 풀리지 않는다 (멱등)")
    void releaseReservation_isIdempotent() {
        Long orderId = checkoutWithQuantity(3);
        reservationExpiryService.releaseReservation(orderId);

        boolean releasedAgain = reservationExpiryService.releaseReservation(orderId);

        assertThat(releasedAgain).isFalse();
        assertThat(reloadStock().getAvailableStocks()).isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("기준 시각보다 오래된 미결제 주문만 회수 대상으로 찾는다")
    void findExpiredOrderIds_onlyReturnsOldPendingOrders() {
        Long oldOrderId = checkoutWithQuantity(1);
        ageOrder(oldOrderId, LocalDateTime.now().minusHours(2));
        Long freshOrderId = checkoutWithQuantity(1);

        List<Long> expired = reservationExpiryService.findExpiredOrderIds(
                LocalDateTime.now().minusMinutes(30), 100);

        assertThat(expired).contains(oldOrderId).doesNotContain(freshOrderId);
    }

    @Test
    @DisplayName("한 주기에 처리할 건수는 batchSize 로 제한된다")
    void findExpiredOrderIds_respectsBatchSize() {
        for (int i = 0; i < 3; i++) {
            ageOrder(checkoutWithQuantity(1), LocalDateTime.now().minusHours(2));
        }

        List<Long> expired = reservationExpiryService.findExpiredOrderIds(
                LocalDateTime.now().minusMinutes(30), 2);

        assertThat(expired).hasSize(2);
    }

    @Test
    @DisplayName("담은 뒤 삭제된 상품은 주문으로 전환되지 않는다")
    void checkout_throwsForDeletedProduct() {
        cartService.addItem(memberId, new CartItemAddRequest(productId, 1));
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.delete();
        productRepository.saveAndFlush(product);

        assertThatThrownBy(() -> cartOrderService.checkout(memberId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        // 실패했으므로 재고 예약도 주문도 남지 않아야 한다.
        assertThat(reloadStock().getAvailableStocks()).isEqualTo(INITIAL_STOCK);
        assertThat(orderRepository.count()).isZero();
    }
}
