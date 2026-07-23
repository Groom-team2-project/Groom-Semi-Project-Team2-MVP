package org.example.groommvp.domain.cart.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.cart.config.CartCacheNames;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 CRUD 서비스.
 *
 * <p>모든 조작은 회원 ID 기준으로 이루어지며, 항목 조작 시 소유권을 검증해
 * 다른 회원의 장바구니 항목에 접근하지 못하도록 격리한다.
 *
 * <p><b>캐싱:</b> 조회({@link #getMyCart})는 회원별로 캐싱하고, 장바구니를 변경하는
 * 모든 메서드는 해당 회원의 캐시를 무효화한다. (캐시 키 = memberId)
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    /**
     * 내 장바구니 조회.
     *
     * <p>장바구니가 없으면 빈 응답을 반환한다. (조회는 쓰기를 유발하지 않는다 —
     * 장바구니는 첫 담기 시점에 생성된다.)
     */
    @Cacheable(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional(readOnly = true)
    public CartResponse getMyCart(Long memberId) {
        return cartRepository.findByMemberIdWithItems(memberId)
                .map(CartResponse::from)
                .orElseGet(() -> CartResponse.empty(memberId));
    }

    /** 상품을 장바구니에 담는다. 이미 담긴 상품이면 수량을 더한다. */
    @CacheEvict(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional
    public CartResponse addItem(Long memberId, CartItemAddRequest request) {
        CartEntity cart = getOrCreateCart(memberId);
        ProductEntity product = productRepository.findById(request.productId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        cart.addItem(product, request.quantity());
        return CartResponse.from(cart);
    }

    /** 장바구니 항목의 수량을 변경한다. */
    @CacheEvict(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional
    public CartResponse updateItemQuantity(Long memberId, Long cartItemId, CartItemUpdateRequest request) {
        CartItemEntity item = getOwnedItem(memberId, cartItemId);
        item.changeQuantity(request.quantity());
        return CartResponse.from(item.getCart());
    }

    /** 장바구니 항목을 삭제한다. */
    @CacheEvict(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional
    public CartResponse removeItem(Long memberId, Long cartItemId) {
        CartItemEntity item = getOwnedItem(memberId, cartItemId);
        CartEntity cart = item.getCart();
        cart.removeItem(item);
        return CartResponse.from(cart);
    }

    /** 장바구니를 비운다. */
    @CacheEvict(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional
    public CartResponse clearCart(Long memberId) {
        return cartRepository.findByMemberIdWithItems(memberId)
                .map(cart -> {
                    cart.clear();
                    return CartResponse.from(cart);
                })
                .orElseGet(() -> CartResponse.empty(memberId));
    }

    /** 회원의 장바구니를 가져오고, 없으면 생성한다. (첫 담기 시점) */
    private CartEntity getOrCreateCart(Long memberId) {
        return cartRepository.findByMemberIdWithItems(memberId)
                .orElseGet(() -> {
                    MemberEntity member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                    return cartRepository.save(CartEntity.init(member));
                });
    }

    /** 항목을 조회하고, 요청 회원이 해당 장바구니의 소유자인지 검증한다. */
    private CartItemEntity getOwnedItem(Long memberId, Long cartItemId) {
        CartItemEntity item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
        if (!item.getCart().isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_FORBIDDEN);
        }
        return item;
    }
}
