package org.example.groommvp.domain.cart.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import java.time.Duration;
import java.util.Optional;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 체크아웃의 <b>재고 락 획득 순서</b>를 결정적으로 검증한다.
 *
 * <p>{@code CartOrderServiceConcurrencyTest} 는 실제 동시 실행으로 "교착하지 않는다"를 보지만,
 * 교착은 타이밍에 의존하므로 <b>정렬을 지워도 항상 실패하지는 않는다.</b> 회귀 방지가 약하다는 뜻이다.
 *
 * <p>그래서 여기서는 락 호출 <b>순서 자체</b>를 단언한다. 장바구니에 담긴 순서와 무관하게 항상
 * 상품 ID 오름차순으로 잠가야 하며, 이 순서가 깨지면 DB 없이도 즉시 실패한다.
 *
 * <p><b>왜 순서가 중요한가:</b> 여러 트랜잭션이 같은 자원들을 <b>서로 다른 순서</b>로 잠그면
 * 교착한다. 모두가 같은 순서(상품 ID 오름차순)로 잠그면 순환 대기가 생길 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class CartOrderServiceLockOrderTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private CartOrderService cartOrderService;

    @BeforeEach
    void setUp() {
        // 결제 마감 시각은 설정값(Duration)이라 목이 아니다. @InjectMocks 는 이 자리에 null 을
        // 넣어버리므로 직접 조립한다.
        cartOrderService = new CartOrderService(cartRepository, stockRepository,
                stockHistoryRepository, orderRepository, orderItemRepository, Duration.ofMinutes(30));
    }

    private static MemberEntity member() {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-1", "u@example.com", "회원");
        ReflectionTestUtils.setField(member, "memberId", MEMBER_ID);
        return member;
    }

    private static ProductEntity product(Long productId, int price) {
        ProductEntity product = ProductEntity.builder()
                .productName("상품" + productId)
                .productPrice(price)
                .build();
        ReflectionTestUtils.setField(product, "productId", productId);
        return product;
    }

    private static StockEntity stock(ProductEntity product) {
        return new StockEntity(product, 1_000);
    }

    @Test
    @DisplayName("담은 순서가 역순이어도 재고 락은 항상 상품 ID 오름차순으로 잡는다")
    void checkout_locksStocksInAscendingProductIdOrder() {
        // 일부러 3 → 1 → 2 순서로 담는다.
        CartEntity cart = CartEntity.init(member());
        ProductEntity third = product(3L, 3_000);
        ProductEntity first = product(1L, 1_000);
        ProductEntity second = product(2L, 2_000);
        cart.addItem(third, 1);
        cart.addItem(first, 1);
        cart.addItem(second, 1);

        given(cartRepository.findByMemberIdWithItems(MEMBER_ID)).willReturn(Optional.of(cart));
        given(stockRepository.findByProductIdWithPessimisticLock(1L))
                .willReturn(Optional.of(stock(first)));
        given(stockRepository.findByProductIdWithPessimisticLock(2L))
                .willReturn(Optional.of(stock(second)));
        given(stockRepository.findByProductIdWithPessimisticLock(3L))
                .willReturn(Optional.of(stock(third)));
        given(orderRepository.save(any(Order.class)))
                .willAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", 42L);
                    return order;
                });
        given(orderItemRepository.save(any(OrderItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(stockHistoryRepository.save(any(StockHistoryEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        cartOrderService.checkout(MEMBER_ID);

        // 담은 순서(3,1,2)가 아니라 상품 ID 오름차순(1,2,3)으로 잠가야 한다.
        InOrder lockOrder = inOrder(stockRepository);
        lockOrder.verify(stockRepository).findByProductIdWithPessimisticLock(1L);
        lockOrder.verify(stockRepository).findByProductIdWithPessimisticLock(2L);
        lockOrder.verify(stockRepository).findByProductIdWithPessimisticLock(3L);
        lockOrder.verifyNoMoreInteractions();
    }
}
