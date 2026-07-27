package org.example.groommvp.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.dto.CartItemUpdateRequest;
import org.example.groommvp.domain.cart.dto.CartResponse;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 장바구니 담기 통합 테스트. (실제 영속화까지 확인)
 *
 * <p>단위 테스트는 리포지토리를 mock 으로 대체하므로 "신규 항목의 PK 가 언제 할당되는가" 를
 * 검증할 수 없다. 여기서는 실제 EntityManager 를 태워, 담기 응답이 곧바로 쓸 수 있는
 * {@code cartItemId} 를 담고 있는지 확인한다.
 */
@SpringBootTest
class CartServiceIntegrationTest {

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

    private Long memberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        MemberEntity member = memberRepository.save(
                MemberEntity.createKakaoMember("cart-integration", "ci@example.com", "장바구니회원"));
        memberId = member.getMemberId();
        productId = productRepository.save(new ProductEntity("티셔츠", 10_000)).getProductId();
    }

    @AfterEach
    void tearDown() {
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("담기 응답의 cartItemId 로 곧바로 수량을 변경할 수 있다")
    void addItem_returnsUsableCartItemId() {
        CartResponse added = cartService.addItem(memberId, new CartItemAddRequest(productId, 2));

        Long cartItemId = added.items().getFirst().cartItemId();
        assertThat(cartItemId).isNotNull();

        // 응답으로 받은 ID 가 실제로 조회/변경 가능해야 한다. (클라이언트가 바로 쓰는 값)
        CartResponse updated = cartService.updateItemQuantity(
                memberId, cartItemId, new CartItemUpdateRequest(5));

        assertThat(updated.totalQuantity()).isEqualTo(5);
        assertThat(updated.totalPrice()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("같은 상품을 다시 담아도 항목 ID 는 유지되고 수량만 누적된다")
    void addItem_keepsCartItemIdWhenMerging() {
        Long firstId = cartService.addItem(memberId, new CartItemAddRequest(productId, 2))
                .items().getFirst().cartItemId();

        CartResponse merged = cartService.addItem(memberId, new CartItemAddRequest(productId, 3));

        assertThat(merged.items()).singleElement().satisfies(item -> {
            assertThat(item.cartItemId()).isEqualTo(firstId);
            assertThat(item.quantity()).isEqualTo(5);
        });
    }
}
