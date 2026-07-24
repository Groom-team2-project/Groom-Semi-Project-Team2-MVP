package org.example.groommvp.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.groommvp.domain.order.dto.PurchaseRequest;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.stock.entity.StockHistoryType;

@SpringBootTest
class PurchaseApiMockMvcTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    void purchaseApiCreatesPendingOrderAndReservesStock() throws Exception {
        ProductEntity product = productRepository.save(new ProductEntity("MockMvc Product", 10000));
        StockEntity stock = stockRepository.save(new StockEntity(product, 10));

        mockMvc.perform(post("/api/v1/products/{productId}/orders", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(product.getProductId()))
                .andExpect(jsonPath("$.data.purchasedQuantity").value(3))
                .andExpect(jsonPath("$.data.remainingStockQuantity").value(7))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        StockEntity savedStock = stockRepository.findById(stock.getStockId()).orElseThrow();

        assertThat(savedStock.getStocks()).isEqualTo(10);
        assertThat(savedStock.getReservedStocks()).isEqualTo(3);
        assertThat(savedStock.getAvailableStocks()).isEqualTo(7);
        assertThat(orderRepository.findAll().getFirst().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(stockHistoryRepository.findAll().getFirst().getChangeType()).isEqualTo(StockHistoryType.RESERVE);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(stockHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void purchaseApiReturnsBadRequestWhenQuantityIsZero() throws Exception {
        ProductEntity product = productRepository.save(new ProductEntity("Invalid Quantity Product", 10000));
        stockRepository.save(new StockEntity(product, 10));

        mockMvc.perform(post("/api/v1/products/{productId}/orders", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(stockHistoryRepository.count()).isZero();
    }

    @Test
    void purchaseApiReturnsBadRequestWhenRequestBodyHasInvalidType() throws Exception {
        ProductEntity product = productRepository.save(new ProductEntity("Invalid Body Product", 10000));
        stockRepository.save(new StockEntity(product, 10));

        mockMvc.perform(post("/api/v1/products/{productId}/orders", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":"invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(stockHistoryRepository.count()).isZero();
    }

    @Test
    void purchaseApiReturnsBadRequestWhenProductIdHasInvalidType() throws Exception {
        mockMvc.perform(post("/api/v1/products/{productId}/orders", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void purchaseApiReturnsNotFoundWhenProductDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/products/{productId}/orders", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void purchaseApiReturnsNotFoundWhenStockDoesNotExist() throws Exception {
        ProductEntity product = productRepository.save(new ProductEntity("No Stock Product", 10000));

        mockMvc.perform(post("/api/v1/products/{productId}/orders", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(stockHistoryRepository.count()).isZero();
    }

    @Test
    void purchaseApiReturnsConflictWhenStockIsNotEnough() throws Exception {
        ProductEntity product = productRepository.save(new ProductEntity("Limited Stock Product", 10000));
        StockEntity stock = stockRepository.save(new StockEntity(product, 1));

        mockMvc.perform(post("/api/v1/products/{productId}/orders", product.getProductId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));

        StockEntity savedStock = stockRepository.findById(stock.getStockId()).orElseThrow();

        assertThat(savedStock.getStocks()).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(stockHistoryRepository.count()).isZero();
    }

    @Test
    void purchaseApiReturnsMethodNotAllowedWhenHttpMethodIsNotSupported() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}/orders", 1L))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void concurrentPurchaseApiCannotExceedStock() throws Exception {
        ProductEntity product = productRepository.save(new ProductEntity("Concurrent API Product", 10000));
        stockRepository.save(new StockEntity(product, 30));

        int requestCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();

                    MvcResult result = mockMvc.perform(post("/api/v1/products/{productId}/orders", product.getProductId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"quantity":1}
                                            """))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        startLatch.countDown();

        assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();

        executorService.shutdown();

        StockEntity savedStock = stockRepository.findAll().getFirst();

        assertThat(successCount.get()).isEqualTo(30);
        assertThat(conflictCount.get()).isEqualTo(70);
        assertThat(savedStock.getStocks()).isEqualTo(30);
        assertThat(savedStock.getReservedStocks()).isEqualTo(30);
        assertThat(savedStock.getAvailableStocks()).isZero();
        assertThat(orderRepository.count()).isEqualTo(30);
        assertThat(orderItemRepository.count()).isEqualTo(30);
        assertThat(stockHistoryRepository.count()).isEqualTo(30);
        assertThat(stockHistoryRepository.findAll())
                .allMatch(history -> history.getChangeType() == StockHistoryType.RESERVE);
    }
}
