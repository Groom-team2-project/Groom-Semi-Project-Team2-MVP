package org.example.groommvp.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.dto.CartItemUpdateRequest;
import org.example.groommvp.domain.cart.dto.CartResponse;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.example.groommvp.domain.cart.entity.CartItemEntity;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 장바구니 서비스 단위 테스트.
 *
 * <p>기획서 E 파트 요구 테스트인 "장바구니 · 회원 격리"를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CartService cartService;

    private static MemberEntity member(Long memberId) {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-" + memberId, "u@example.com", "회원");
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return member;
    }

    private static ProductEntity product(Long productId, String name, int price) {
        ProductEntity product = ProductEntity.builder()
                .productName(name)
                .productPrice(price)
                .build();
        ReflectionTestUtils.setField(product, "productId", productId);
        return product;
    }

    private static CartEntity cartOf(Long memberId) {
        return CartEntity.init(member(memberId));
    }

    @Nested
    @DisplayName("회원 격리")
    class MemberIsolation {

        @Test
        @DisplayName("다른 회원의 장바구니 항목은 수량을 변경할 수 없다")
        void updateItemQuantity_throwsForOtherMembersItem() {
            CartEntity ownersCart = cartOf(OWNER_ID);
            CartItemEntity item = ownersCart.addItem(product(1L, "티셔츠", 10_000), 1);
            given(cartItemRepository.findById(10L)).willReturn(Optional.of(item));

            assertThatThrownBy(() ->
                    cartService.updateItemQuantity(OTHER_MEMBER_ID, 10L, new CartItemUpdateRequest(5)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_FORBIDDEN);

            // 남의 항목은 수량이 바뀌지 않아야 한다.
            assertThat(item.getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 회원의 장바구니 항목은 삭제할 수 없다")
        void removeItem_throwsForOtherMembersItem() {
            CartEntity ownersCart = cartOf(OWNER_ID);
            CartItemEntity item = ownersCart.addItem(product(1L, "티셔츠", 10_000), 1);
            given(cartItemRepository.findById(10L)).willReturn(Optional.of(item));

            assertThatThrownBy(() -> cartService.removeItem(OTHER_MEMBER_ID, 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_FORBIDDEN);

            // 남의 장바구니에서 항목이 사라지지 않아야 한다.
            assertThat(ownersCart.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("본인 항목은 수량을 변경할 수 있다")
        void updateItemQuantity_succeedsForOwner() {
            CartEntity ownersCart = cartOf(OWNER_ID);
            CartItemEntity item = ownersCart.addItem(product(1L, "티셔츠", 10_000), 1);
            given(cartItemRepository.findById(10L)).willReturn(Optional.of(item));

            CartResponse response = cartService.updateItemQuantity(
                    OWNER_ID, 10L, new CartItemUpdateRequest(5));

            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(response.totalQuantity()).isEqualTo(5);
            assertThat(response.totalPrice()).isEqualTo(50_000);
        }

        @Test
        @DisplayName("존재하지 않는 항목이면 404 성격의 예외가 발생한다")
        void getOwnedItem_throwsWhenNotFound() {
            given(cartItemRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(OWNER_ID, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("조회")
    class GetMyCart {

        @Test
        @DisplayName("장바구니가 없으면 빈 응답을 반환하고 새로 만들지 않는다")
        void getMyCart_returnsEmptyWithoutCreating() {
            given(cartRepository.findByMemberIdWithItems(OWNER_ID)).willReturn(Optional.empty());

            CartResponse response = cartService.getMyCart(OWNER_ID);

            assertThat(response.cartId()).isNull();
            assertThat(response.memberId()).isEqualTo(OWNER_ID);
            assertThat(response.items()).isEmpty();
            assertThat(response.totalQuantity()).isZero();
            assertThat(response.totalPrice()).isZero();
            // 조회는 쓰기를 유발하지 않아야 캐싱이 안전하다.
            verify(cartRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("담긴 항목의 수량과 금액을 합산해 반환한다")
        void getMyCart_summarizesQuantityAndPrice() {
            CartEntity cart = cartOf(OWNER_ID);
            cart.addItem(product(1L, "티셔츠", 10_000), 2);
            cart.addItem(product(2L, "바지", 20_000), 1);
            given(cartRepository.findByMemberIdWithItems(OWNER_ID)).willReturn(Optional.of(cart));

            CartResponse response = cartService.getMyCart(OWNER_ID);

            assertThat(response.items()).hasSize(2);
            assertThat(response.totalQuantity()).isEqualTo(3);
            assertThat(response.totalPrice()).isEqualTo(40_000);
        }
    }

    @Nested
    @DisplayName("담기")
    class AddItem {

        @Test
        @DisplayName("장바구니가 없으면 이때 생성한다")
        void addItem_createsCartOnFirstAdd() {
            given(cartRepository.findByMemberIdWithItems(OWNER_ID)).willReturn(Optional.empty());
            given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));
            given(cartRepository.save(org.mockito.ArgumentMatchers.any(CartEntity.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(productRepository.findById(1L)).willReturn(Optional.of(product(1L, "티셔츠", 10_000)));

            CartResponse response = cartService.addItem(OWNER_ID, new CartItemAddRequest(1L, 2));

            assertThat(response.totalQuantity()).isEqualTo(2);
            verify(cartRepository).save(org.mockito.ArgumentMatchers.any(CartEntity.class));
        }

        @Test
        @DisplayName("이미 담긴 상품이면 수량을 더한다")
        void addItem_mergesQuantityForSameProduct() {
            CartEntity cart = cartOf(OWNER_ID);
            cart.addItem(product(1L, "티셔츠", 10_000), 2);
            given(cartRepository.findByMemberIdWithItems(OWNER_ID)).willReturn(Optional.of(cart));
            given(productRepository.findById(1L)).willReturn(Optional.of(product(1L, "티셔츠", 10_000)));

            CartResponse response = cartService.addItem(OWNER_ID, new CartItemAddRequest(1L, 3));

            assertThat(response.items()).hasSize(1);
            assertThat(response.totalQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("삭제된 상품은 담을 수 없다")
        void addItem_throwsForDeletedProduct() {
            CartEntity cart = cartOf(OWNER_ID);
            ProductEntity deleted = product(1L, "티셔츠", 10_000);
            deleted.delete();
            given(cartRepository.findByMemberIdWithItems(OWNER_ID)).willReturn(Optional.of(cart));
            given(productRepository.findById(1L)).willReturn(Optional.of(deleted));

            assertThatThrownBy(() -> cartService.addItem(OWNER_ID, new CartItemAddRequest(1L, 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
        }
    }
}
