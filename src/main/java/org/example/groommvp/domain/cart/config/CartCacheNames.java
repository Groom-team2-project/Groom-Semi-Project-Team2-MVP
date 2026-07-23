package org.example.groommvp.domain.cart.config;

/**
 * 장바구니 캐시 이름 상수.
 *
 * <p>{@code @Cacheable}/{@code @CacheEvict} 의 {@code cacheNames} 는 문자열이라
 * 오타가 나도 컴파일 단계에서 걸리지 않는다. 상수로 모아 조회와 무효화가
 * 반드시 같은 캐시를 가리키도록 강제한다.
 */
public final class CartCacheNames {

    /** 회원별 장바구니 조회 캐시. 키 = memberId. */
    public static final String CART = "carts";

    private CartCacheNames() {
    }
}
