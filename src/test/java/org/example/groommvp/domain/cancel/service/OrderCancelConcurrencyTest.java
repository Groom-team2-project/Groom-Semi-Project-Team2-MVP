package org.example.groommvp.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderCancelConcurrencyTest {

    @Autowired private OrderCancelService orderCancelService;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockHistoryRepository stockHistoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    // 각 테스트 후 데이터 정리 (자식 → 부모 순서로 삭제)
    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 주문에 동시에 100개 취소 요청이 와도 딱 1번만 취소되고 재고도 1번만 복구된다")
    void concurrentCancel_onlyOnce() throws InterruptedException {
        // given: 상품 / 재고(0) / 주문(COMPLETED) / 주문품목(2개) 준비
        ProductEntity product = productRepository.save(new ProductEntity("Test Product", 10000));
        stockRepository.save(new StockEntity(product, 0));                 // 취소 전 재고 0
        Order order = orderRepository.save(new Order(20000));              // COMPLETED 주문
        orderItemRepository.save(new OrderItem(order, product, 2, 10000)); // 2개 구매했던 품목
        Long orderId = order.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);        // 출발 신호총
        CountDownLatch doneLatch = new CountDownLatch(threadCount); // 100개 완료 대기
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // 100개 스레드가 동시에 같은 주문을 취소 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();                  // 신호총 울릴 때까지 대기
                    orderCancelService.cancel(orderId);
                    successCount.incrementAndGet();      // 취소 성공
                } catch (BusinessException e) {
                    failCount.incrementAndGet();         // 중복 취소로 차단됨
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();                          // 탕! 100개 동시 출발
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();

        // then: 락 덕분에 딱 1번만 성공, 나머지 99개는 중복 취소로 차단
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        Order canceled = orderRepository.findById(orderId).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);        // 취소됨
        assertThat(stockRepository.findAll().get(0).getStocks()).isEqualTo(2);   // 0 + 2, 딱 1번 복구
        assertThat(stockHistoryRepository.count()).isEqualTo(1);                 // RESTORE 이력 1건뿐
    }
}
