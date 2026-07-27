package org.example.groommvp.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 장바구니 주문(체크아웃) 교차 락 테스트.
 *
 * <p>같은 두 상품을 <b>서로 반대 순서</b>로 담은 회원들이 동시에 체크아웃할 때 교착하지 않는지
 * 검증한다. 담은 순서대로 재고 락을 잡으면 A 는 상품1→상품2, B 는 상품2→상품1 로 잠가
 * 서로를 기다린다. ({@code CartOrderService#checkout} 의 상품 ID 정렬)
 */
@SpringBootTest
class CartOrderServiceConcurrencyTest {

    private static final int INITIAL_STOCK = 500;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartOrderService cartOrderService;

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

    @Test
    @DisplayName("담은 순서가 서로 반대인 회원들이 동시에 체크아웃해도 교착하지 않는다")
    void concurrentCheckout_withOppositeItemOrder_doesNotDeadlock() throws InterruptedException {
        ProductEntity productA = newProductWithStock("상품A", 1_000);
        ProductEntity productB = newProductWithStock("상품B", 2_000);

        int memberCount = 16;
        List<Long> memberIds = new java.util.ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            MemberEntity member = memberRepository.save(
                    MemberEntity.createKakaoMember("checkout-" + i, "co" + i + "@example.com", "회원" + i));
            Long memberId = member.getMemberId();
            memberIds.add(memberId);

            // 절반은 A→B, 나머지 절반은 B→A 순서로 담는다.
            if (i % 2 == 0) {
                cartService.addItem(memberId, new CartItemAddRequest(productA.getProductId(), 1));
                cartService.addItem(memberId, new CartItemAddRequest(productB.getProductId(), 1));
            } else {
                cartService.addItem(memberId, new CartItemAddRequest(productB.getProductId(), 1));
                cartService.addItem(memberId, new CartItemAddRequest(productA.getProductId(), 1));
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(memberCount);
        CountDownLatch readyLatch = new CountDownLatch(memberCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(memberCount);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (Long memberId : memberIds) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        cartOrderService.checkout(memberId);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            assertThat(doneLatch.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(orderRepository.findAll()).hasSize(memberCount);
        // 각 회원이 두 상품을 1개씩 예약했으므로 가용 재고가 정확히 memberCount 만큼 줄어야 한다.
        assertThat(availableStock(productA)).isEqualTo(INITIAL_STOCK - memberCount);
        assertThat(availableStock(productB)).isEqualTo(INITIAL_STOCK - memberCount);
    }

    private ProductEntity newProductWithStock(String name, int price) {
        ProductEntity product = productRepository.save(new ProductEntity(name, price));
        stockRepository.save(new StockEntity(product, INITIAL_STOCK));
        return product;
    }

    private int availableStock(ProductEntity product) {
        return stockRepository.findAll().stream()
                .filter(stock -> stock.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow()
                .getAvailableStocks();
    }
}
