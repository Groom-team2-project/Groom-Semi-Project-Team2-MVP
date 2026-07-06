package org.example.groommvp.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.dto.StockInRequest;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 입고 동시성 테스트.
 *
 * <p>여러 스레드가 동시에 같은 상품을 입고할 때, 비관적 락({@code PESSIMISTIC_WRITE})으로
 * 재고 증가가 유실 없이 직렬화되는지 검증한다. (PurchaseServiceConcurrencyTest 패턴과 동일)
 */
@SpringBootTest
class StockServiceConcurrencyTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("N명이 동시에 1개씩 입고하면 재고가 정확히 N만큼 증가하고 이력도 N건 남는다")
    void concurrentStockInIncreasesExactly() throws InterruptedException {
        // given
        ProductEntity product = productRepository.save(new ProductEntity("동시입고상품", 10000));
        stockRepository.save(new StockEntity(product, 0));

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        // when: 100개 스레드가 동시에 1개씩 입고
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    stockService.stockIn(product.getProductId(), new StockInRequest(1, "동시 입고"));
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
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

        // then: 유실 없이 정확히 100 증가, 이력 100건
        StockEntity savedStock = stockRepository.findAll().getFirst();
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(savedStock.getStocks()).isEqualTo(100);
        assertThat(stockHistoryRepository.count()).isEqualTo(100);
    }
}
