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
import org.example.groommvp.domain.stock.entity.StockHistoryType;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class OrderCancelConcurrencyTest {

    @Autowired private OrderCancelService orderCancelService;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockHistoryRepository stockHistoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    // 각 테스트 후 데이터 정리 (자식 → 부모 순서로 삭제)
    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    // 결제 완료 주문은 취소 API로 취소할 수 없고(환불 API로만 처리) 아래 결제 대기 케이스가
    // 동일한 동시성 보장(요청 100개 중 1건만 성공)을 검증하므로, COMPLETED 동시 취소 테스트는 제거했다.

    @Test
    @DisplayName("PENDING_PAYMENT 주문에 취소 요청 100개가 와도 예약은 한 번만 해제된다")
    void concurrentCancel_pendingPayment_releasesOnlyOnce()
            throws InterruptedException {
        // given
        ProductEntity product = productRepository.save(
                ProductEntity.builder()
                        .productName("Pending Product")
                        .productPrice(10000)
                        .build()
        );

        StockEntity stock = new StockEntity(product, 10);
        stock.reserve(2);
        stockRepository.save(stock);

        Long memberId = 100L;
        Order order = orderRepository.save(
                Order.pendingPayment(memberId, 20000L)
        );

        orderItemRepository.save(
                new OrderItem(order, product, 2, 10000)
        );

        Long orderId = order.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    orderCancelService.cancel(orderId, memberId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        StockEntity savedStock = stockRepository.findAll().get(0);
        assertThat(savedStock.getStocks()).isEqualTo(10);
        assertThat(savedStock.getReservedStocks()).isZero();

        assertThat(stockHistoryRepository.count()).isEqualTo(1);
        assertThat(stockHistoryRepository.findAll().get(0).getChangeType())
                .isEqualTo(StockHistoryType.RELEASE);
    }
}
