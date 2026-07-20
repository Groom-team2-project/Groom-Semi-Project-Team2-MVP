package org.example.groommvp.domain.cart.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.global.entity.BaseEntity;

/**
 * 회원 1인당 1개의 장바구니를 표현하는 엔티티. (테이블: carts)
 *
 * <p>장바구니 항목({@link CartItemEntity})의 애그리거트 루트로, 항목 추가/삭제는
 * 이 엔티티를 통해 수행하여 컬렉션 일관성을 유지한다.
 *
 * <p><b>회원 참조:</b> 회원({@link MemberEntity})과 1:1. FK 컬럼은 {@code member_id}
 * 이며 회원당 1개(unique)로 제한한다.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case 이며,
 * 컬럼명은 {@code @Column(name = "...")} 으로 명시한다. (팀 컨벤션)
 */
@Entity
@Getter
@Table(name = "carts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    /** 장바구니 소유 회원 (1:1). FK 컬럼은 member_id, 회원당 1개(unique). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private MemberEntity member;

    /** 장바구니 항목 목록. 장바구니 삭제 시 함께 삭제되고, 컬렉션에서 제거되면 고아 삭제된다. */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CartItemEntity> items = new ArrayList<>();

    @Builder
    public CartEntity(MemberEntity member) {
        this.member = member;
    }

    /** 회원의 빈 장바구니를 생성한다. */
    public static CartEntity init(MemberEntity member) {
        return CartEntity.builder().member(member).build();
    }

    /** 이 장바구니의 소유자인지 확인한다. */
    public boolean isOwnedBy(Long memberId) {
        return this.member.getMemberId().equals(memberId);
    }

    /** 상품 ID로 담긴 항목을 찾는다. */
    public Optional<CartItemEntity> findItemByProduct(Long productId) {
        return items.stream()
                .filter(item -> item.getProduct().getProductId().equals(productId))
                .findFirst();
    }

    /**
     * 상품을 담는다. 이미 담긴 상품이면 수량을 더하고, 아니면 새 항목을 추가한다.
     *
     * @return 담기 후의 항목
     */
    public CartItemEntity addItem(ProductEntity product, int quantity) {
        return findItemByProduct(product.getProductId())
                .map(existing -> {
                    existing.addQuantity(quantity);
                    return existing;
                })
                .orElseGet(() -> {
                    CartItemEntity item = CartItemEntity.of(this, product, quantity);
                    items.add(item);
                    return item;
                });
    }

    /** 항목을 장바구니에서 제거한다. (고아 삭제로 DB 에서도 삭제) */
    public void removeItem(CartItemEntity item) {
        items.remove(item);
    }

    /** 장바구니를 비운다. */
    public void clear() {
        items.clear();
    }
}
