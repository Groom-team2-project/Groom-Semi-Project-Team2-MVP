package org.example.groommvp.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.groommvp.domain.cart.config.CartCacheNames;
import org.example.groommvp.domain.cart.dto.CartResponse;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 장바구니 조회 캐싱 동작 테스트. (기획서 E 파트 "캐시 히트" 검증)
 *
 * <p>여기서 검증하는 것은 <b>캐싱 애너테이션의 동작</b>(히트 시 DB 재조회 생략, 변경 시 무효화)이다.
 * Redis 서버 없이 돌아가도록 캐시 저장소만 인메모리({@link ConcurrentMapCacheManager})로 바꿔
 * 실행한다. 실제 Redis 연동(직렬화·TTL)은 {@code CartCacheConfig} 의 책임이라 여기서 다루지 않는다.
 */
@SpringJUnitConfig(CartServiceCacheTest.CacheTestConfig.class)
class CartServiceCacheTest {

    private static final Long MEMBER_ID = 1L;

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CartCacheNames.CART);
        }

        @Bean
        CartRepository cartRepository() {
            return mock(CartRepository.class);
        }

        @Bean
        CartItemRepository cartItemRepository() {
            return mock(CartItemRepository.class);
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        MemberRepository memberRepository() {
            return mock(MemberRepository.class);
        }

        @Bean
        CartService cartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                                ProductRepository productRepository, MemberRepository memberRepository) {
            return new CartService(cartRepository, cartItemRepository, productRepository, memberRepository);
        }
    }

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CacheManager cacheManager;

    private static MemberEntity member(Long memberId) {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-" + memberId, "u@example.com", "회원");
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return member;
    }

    private static ProductEntity product(Long productId, int price) {
        ProductEntity product = ProductEntity.builder().productName("티셔츠").productPrice(price).build();
        ReflectionTestUtils.setField(product, "productId", productId);
        return product;
    }

    private static CartEntity cartWithItem(Long memberId, int quantity) {
        CartEntity cart = CartEntity.init(member(memberId));
        cart.addItem(product(1L, 10_000), quantity);
        return cart;
    }

    @BeforeEach
    void clearCache() {
        reset(cartRepository);
        cacheManager.getCache(CartCacheNames.CART).clear();
    }

    @Test
    @DisplayName("같은 회원의 장바구니를 다시 조회하면 캐시 히트로 DB를 다시 조회하지 않는다")
    void getMyCart_secondCallHitsCache() {
        given(cartRepository.findByMemberIdWithItems(MEMBER_ID))
                .willReturn(Optional.of(cartWithItem(MEMBER_ID, 2)));

        CartResponse first = cartService.getMyCart(MEMBER_ID);
        CartResponse second = cartService.getMyCart(MEMBER_ID);

        assertThat(first.totalQuantity()).isEqualTo(2);
        assertThat(second.totalQuantity()).isEqualTo(2);
        // 두 번 호출했지만 DB 조회는 한 번뿐이어야 한다.
        verify(cartRepository, times(1)).findByMemberIdWithItems(MEMBER_ID);
    }

    @Test
    @DisplayName("회원마다 캐시 키가 분리되어 다른 회원의 장바구니가 섞이지 않는다")
    void getMyCart_cachesPerMember() {
        given(cartRepository.findByMemberIdWithItems(1L))
                .willReturn(Optional.of(cartWithItem(1L, 2)));
        given(cartRepository.findByMemberIdWithItems(2L))
                .willReturn(Optional.of(cartWithItem(2L, 5)));

        CartResponse first = cartService.getMyCart(1L);
        CartResponse second = cartService.getMyCart(2L);

        assertThat(first.memberId()).isEqualTo(1L);
        assertThat(first.totalQuantity()).isEqualTo(2);
        assertThat(second.memberId()).isEqualTo(2L);
        assertThat(second.totalQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("장바구니를 비우면 캐시가 무효화되어 다음 조회는 DB를 다시 읽는다")
    void clearCart_evictsCache() {
        given(cartRepository.findByMemberIdWithItems(MEMBER_ID))
                .willReturn(Optional.of(cartWithItem(MEMBER_ID, 2)));

        cartService.getMyCart(MEMBER_ID);          // 1회 조회 → 캐시 적재
        cartService.getMyCart(MEMBER_ID);          // 캐시 히트 (DB 조회 없음)
        cartService.clearCart(MEMBER_ID);          // 변경 → 캐시 무효화
        cartService.getMyCart(MEMBER_ID);          // 캐시 미스 → DB 재조회

        // 최초 조회 1회 + clearCart 내부 조회 1회 + 무효화 후 재조회 1회 = 3회
        verify(cartRepository, times(3)).findByMemberIdWithItems(MEMBER_ID);
    }

    @Test
    @DisplayName("무효화 후 조회하면 변경된 결과가 반영된다 (stale 데이터가 남지 않는다)")
    void clearCart_returnsFreshDataAfterEviction() {
        given(cartRepository.findByMemberIdWithItems(MEMBER_ID))
                .willReturn(Optional.of(cartWithItem(MEMBER_ID, 2)));
        assertThat(cartService.getMyCart(MEMBER_ID).totalQuantity()).isEqualTo(2);

        // 장바구니가 비워진 뒤에는 빈 상태가 조회되도록 바꾼다.
        CartEntity emptied = CartEntity.init(member(MEMBER_ID));
        given(cartRepository.findByMemberIdWithItems(MEMBER_ID)).willReturn(Optional.of(emptied));
        cartService.clearCart(MEMBER_ID);

        assertThat(cartService.getMyCart(MEMBER_ID).totalQuantity()).isZero();
    }
}
