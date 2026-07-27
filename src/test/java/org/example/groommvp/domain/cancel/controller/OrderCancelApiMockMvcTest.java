package org.example.groommvp.domain.cancel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.groommvp.domain.auth.service.JwtTokenProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class OrderCancelApiMockMvcTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockHistoryRepository stockHistoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("토큰 없이 주문 취소를 요청하면 401을 반환한다")
    void cancelWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 회원의 토큰으로 주문 취소를 요청하면 403을 반환한다")
    void cancelOtherMembersOrderReturnsForbidden() throws Exception {
        MemberEntity owner = saveMember("owner-provider", "owner@example.com", "owner");
        MemberEntity requester = saveMember("requester-provider", "requester@example.com", "requester");
        OrderFixture fixture = saveCompletedOrder(owner.getMemberId());
        String accessToken = jwtTokenProvider.createAccessToken(requester);

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", fixture.order().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ORDER_FORBIDDEN"));

        Order savedOrder = orderRepository.findById(fixture.order().getId()).orElseThrow();
        StockEntity savedStock = stockRepository.findById(fixture.stock().getStockId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(savedStock.getStocks()).isEqualTo(8);
        assertThat(stockHistoryRepository.count()).isZero();
    }

    @Test
    @DisplayName("본인 토큰으로 주문 취소를 요청하면 200을 반환한다")
    void cancelOwnOrderReturnsOk() throws Exception {
        MemberEntity owner = saveMember("owner-provider", "owner@example.com", "owner");
        OrderFixture fixture = saveCompletedOrder(owner.getMemberId());
        String accessToken = jwtTokenProvider.createAccessToken(owner);

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", fixture.order().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(fixture.order().getId()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        Order savedOrder = orderRepository.findById(fixture.order().getId()).orElseThrow();
        StockEntity savedStock = stockRepository.findById(fixture.stock().getStockId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(savedStock.getStocks()).isEqualTo(10);
        assertThat(stockHistoryRepository.count()).isEqualTo(1);
        assertThat(stockHistoryRepository.findAll().getFirst().getChangeType())
                .isEqualTo(StockHistoryType.RESTORE);
    }

    private MemberEntity saveMember(String providerId, String email, String nickname) {
        return memberRepository.save(
                MemberEntity.createKakaoMember(providerId, email, nickname)
        );
    }

    private OrderFixture saveCompletedOrder(Long memberId) {
        ProductEntity product = productRepository.save(
                ProductEntity.builder()
                        .productName("Cancel API Product")
                        .productPrice(10000)
                        .build()
        );
        StockEntity stock = stockRepository.save(new StockEntity(product, 8));
        Order order = orderRepository.save(new Order(memberId, 20000L));
        orderItemRepository.save(new OrderItem(order, product, 2, 10000));
        return new OrderFixture(order, stock);
    }

    private record OrderFixture(Order order, StockEntity stock) {
    }
}
