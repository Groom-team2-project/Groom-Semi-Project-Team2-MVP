package org.example.groommvp.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 장바구니 최초 생성 경합 테스트.
 *
 * <p>장바구니가 없는 회원에게 여러 요청이 <b>동시에</b> 담을 때, {@code carts.member_id} 유니크
 * 제약으로 일부 요청이 실패(500)하지 않고 모두 반영되는지 검증한다.
 * ({@code CartService#getOrCreateCart} 의 회원 락 직렬화)
 */
@SpringBootTest
class CartServiceConcurrencyTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("장바구니가 없는 회원에게 동시에 담아도 장바구니는 하나이고 모든 요청이 성공한다")
    void concurrentFirstAdd_createsSingleCartWithoutFailure() throws InterruptedException {
        MemberEntity member = memberRepository.save(
                MemberEntity.createKakaoMember("cart-concurrent", "c@example.com", "장바구니회원"));
        Long memberId = member.getMemberId();
        ProductEntity product = productRepository.save(ProductEntity.builder().productName("티셔츠").productPrice(10_000).productImage("test.png").build());
        Long productId = product.getProductId();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        cartService.addItem(memberId, new CartItemAddRequest(productId, 1));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(cartRepository.findAll()).hasSize(1);
        // 같은 상품이므로 항목은 1개로 병합되고 수량은 요청 수만큼 누적된다.
        // (캐시를 타지 않도록 리포지토리로 직접 확인한다)
        assertThat(cartItemRepository.findAll())
                .singleElement()
                .extracting(item -> item.getQuantity())
                .isEqualTo(threadCount);
    }
}
