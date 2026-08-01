package org.example.groommvp.domain.cart.service;

import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        // 신규 항목은 cascade 로 flush 시점에 INSERT 되므로 그 전에는 cartItemId 가 null 이다.
        // 클라이언트가 방금 담은 항목을 바로 수량 변경/삭제할 수 있도록 여기서 ID 를 확정한다.
        cartRepository.flush();
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

    /**
     * 회원의 장바구니를 가져오고, 없으면 생성한다. (첫 담기 시점)
     *
     * <p><b>회원 행을 잠가 이 회원의 담기를 통째로 직렬화한다.</b> 두 가지 경합을 한 번에 막는다.
     * <ul>
     *   <li><b>수량 병합 유실:</b> 같은 상품을 다시 담으면 {@code quantity += n} 인
     *       read-modify-write 가 일어난다. 락이 없으면 동시 요청이 같은 수량을 읽고 각자
     *       갱신해 담은 수량이 사라진다.</li>
     *   <li><b>최초 생성 중복:</b> {@code carts.member_id} 는 유니크라, 동시 첫 담기가 각자
     *       {@code save(init)} 하면 한 쪽이 제약 위반으로 실패한다.</li>
     * </ul>
     *
     * <p><b>왜 장바구니 행이 아니라 회원 행을 잠그는가:</b> 장바구니는 아직 없을 수 있고,
     * <b>존재하지 않는 행</b>을 {@code SELECT ... FOR UPDATE} 하면 InnoDB 는 레코드 락이 아니라
     * <b>갭 락</b>을 잡는다. 갭 락끼리는 공존하지만 뒤따르는 INSERT 의 insert-intention 락과는
     * 충돌하므로, 서로 다른 회원의 첫 담기끼리도 서로의 갭 락을 기다리며 교착한다. (32 스레드
     * 부하에서 5% 가 {@code Deadlock found} 로 실패하는 것을 확인했다. H2 에는 갭 락이 없어
     * 드러나지 않는다.) 회원 행은 <b>반드시 존재</b>하므로 레코드 락만 잡혀 이 문제가 없다.
     *
     * <p><b>순서가 중요하다.</b> 회원 락을 <b>먼저</b> 잡고 그 뒤에 장바구니를 읽어야 한다.
     * REPEATABLE READ 의 읽기 뷰는 첫 <b>일반</b> 읽기에서 만들어지고 락 조회는 만들지 않으므로,
     * 락을 잡은 뒤 읽으면 앞서 락을 쥐었던 트랜잭션이 커밋한 장바구니가 보인다. 반대로 읽고
     * 나서 잠그면 낡은 스냅샷을 보고 중복 생성을 시도하게 된다.
     */
    private CartEntity getOrCreateCart(Long memberId) {
        // 반드시 존재하는 회원 행을 잠근다 → 레코드 락만 잡힌다.
        MemberEntity member = memberRepository.findByIdWithPessimisticLock(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 락을 잡은 뒤의 첫 일반 읽기다. 락 조회는 읽기 뷰를 만들지 않으므로 스냅샷이 지금
        // 잡히고, 앞서 락을 쥐었던 트랜잭션이 만든 장바구니도 보인다.
        try {
            return cartRepository.findByMemberIdWithItems(memberId)
                    .orElseGet(() -> cartRepository.saveAndFlush(CartEntity.init(member)));
        } catch (DataIntegrityViolationException e) {
            // 위 직렬화가 어긋나 중복 생성이 시도된 경우의 마지막 안전망.
            // 원인을 함께 넘겨야 실제로 깨진 제약이 carts.member_id 유니크인지 다른 것인지 로그에서 가려낼 수 있다.
            throw new BusinessException(ErrorCode.CART_BUSY, e);
        }
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
