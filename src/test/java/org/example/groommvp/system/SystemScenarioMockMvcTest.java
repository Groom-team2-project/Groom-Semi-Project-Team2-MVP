package org.example.groommvp.system;

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

@SpringBootTest
class SystemScenarioMockMvcTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void productStockPurchaseScenarioWorksThroughApis() throws Exception {
        createProduct("System Product", 10000, 1);
        Long productId = productRepository.findAll().getFirst().getProductId();

        mockMvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productName").value("System Product"))
                .andExpect(jsonPath("$.data.productPrice").value(10000))
                .andExpect(jsonPath("$.data.stocks").value(1));

        stockIn(productId, 99, "initial stock")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.type").value("INBOUND"))
                .andExpect(jsonPath("$.data.changedQty").value(99))
                .andExpect(jsonPath("$.data.currentStocks").value(100));

        mockMvc.perform(get("/api/v1/products/{productId}/stock-histories", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("INBOUND"));

        purchase(productId, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.purchasedQuantity").value(3))
                .andExpect(jsonPath("$.data.remainingStockQuantity").value(97));

        mockMvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocks").value(97));

        mockMvc.perform(get("/api/v1/products/{productId}/stock-histories", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("DECREASE"))
                .andExpect(jsonPath("$.data[0].changedQty").value(3))
                .andExpect(jsonPath("$.data[1].type").value("INBOUND"));

        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(stockHistoryRepository.count()).isEqualTo(2);
    }

    @Test
    void concurrentPurchaseScenarioCannotExceedStockThroughApis() throws Exception {
        createProduct("Concurrent System Product", 10000, 1);
        Long productId = productRepository.findAll().getFirst().getProductId();
        stockIn(productId, 29, "concurrency stock").andExpect(status().isCreated());

        int requestCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();
                    MvcResult result = purchase(productId, 1).andReturn();

                    int responseStatus = result.getResponse().getStatus();
                    if (responseStatus == 201) {
                        successCount.incrementAndGet();
                    } else if (responseStatus == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedCount.incrementAndGet();
                    }
                } catch (Exception exception) {
                    unexpectedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        startLatch.countDown();

        assertThat(doneLatch.await(20, TimeUnit.SECONDS)).isTrue();

        executorService.shutdown();

        StockEntity stock = stockRepository.findByProduct_ProductId(productId).orElseThrow();

        assertThat(successCount.get()).isEqualTo(30);
        assertThat(conflictCount.get()).isEqualTo(70);
        assertThat(unexpectedCount.get()).isZero();
        assertThat(stock.getStocks()).isZero();
        assertThat(orderRepository.count()).isEqualTo(30);
        assertThat(orderItemRepository.count()).isEqualTo(30);
        assertThat(stockHistoryRepository.count()).isEqualTo(31);

        mockMvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stocks").value(0));
    }

    private void createProduct(String name, int price, int stocks) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productName": "%s",
                                  "productPrice": %d,
                                  "stocks": %d
                                }
                                """.formatted(name, price, stocks)))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(201);
    }

    private org.springframework.test.web.servlet.ResultActions stockIn(
            Long productId,
            int quantity,
            String reason
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/products/{productId}/stock-in", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "quantity": %d,
                          "reason": "%s"
                        }
                        """.formatted(quantity, reason)));
    }

    private org.springframework.test.web.servlet.ResultActions purchase(Long productId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/products/{productId}/orders", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "quantity": %d
                        }
                        """.formatted(quantity)));
    }

    private void cleanUp() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }
}
