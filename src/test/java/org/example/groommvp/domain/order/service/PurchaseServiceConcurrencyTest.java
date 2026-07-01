package org.example.groommvp.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.groommvp.domain.order.dto.PurchaseRequest;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class PurchaseServiceConcurrencyTest {
    @Autowired
    private PurchaseService purchaseService;

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

    @AfterEach
    // 모든 테스트 데이터 삭제
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    // 상품은 총 N개 있다고 할 때, N명의 사용자가 1번씩 동시에 주문할 경우 정확하게 N개의 주문만 생성되는가
    void concurrentPurchaseDecreaseStockExactly() throws InterruptedException {
        ProductEntity product = productRepository.save(
                new ProductEntity("Test Product", 10000)
        );

        stockRepository.save(new StockEntity(product, 100));

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();

                    purchaseService.purchase(
                            product.getProductId(),
                            new PurchaseRequest(1)
                    );

                    successCount.incrementAndGet();
                } catch (BusinessException ignored) {
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        startLatch.countDown();

        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

        executorService.shutdown();

        StockEntity savedStock = stockRepository.findAll().getFirst();

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(savedStock.getStocks()).isZero();
        assertThat(orderRepository.count()).isEqualTo(100);
        assertThat(orderItemRepository.count()).isEqualTo(100);
        assertThat(stockHistoryRepository.count()).isEqualTo(100);
    }

    @Test
    // 상품이 총 N개 있을 때, N+@명의 사용자가 1번씩 동시에 주문할 경우, 정확히 N개의 주문만 생성되는가
    void concurrentPurchaseCannotExceedStock() throws InterruptedException {
        ProductEntity product = productRepository.save(
                new ProductEntity("Limited Product", 10000)
        );
        stockRepository.save(new StockEntity(product, 30));

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();

                    purchaseService.purchase(
                            product.getProductId(),
                            new PurchaseRequest(1)
                    );

                    successCount.incrementAndGet();
                } catch (BusinessException exception) {
                    failCount.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        startLatch.countDown();

        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

        executorService.shutdown();

        StockEntity savedStock = stockRepository.findAll().getFirst();

        assertThat(successCount.get()).isEqualTo(30);
        assertThat(failCount.get()).isEqualTo(70);
        assertThat(savedStock.getStocks()).isZero();
        assertThat(orderRepository.count()).isEqualTo(30);
        assertThat(orderItemRepository.count()).isEqualTo(30);
        assertThat(stockHistoryRepository.count()).isEqualTo(30);
    }
}
