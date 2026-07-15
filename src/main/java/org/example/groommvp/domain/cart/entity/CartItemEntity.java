package org.example.groommvp.domain.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

/**
 * 장바구니에 담긴 개별 상품 항목. (테이블: cart_items)
 *
 * <p>장바구니({@link CartEntity})와 다대일(N:1), 상품({@link ProductEntity})과 다대일(N:1).
 * 항목 생성/수량 변경은 {@link CartEntity} 를 통해 수행하는 것을 권장한다.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case. (팀 컨벤션)
 */
@Entity
@Getter
@Table(name = "cart_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemId;

    /** 항목이 속한 장바구니 (N:1). FK 컬럼은 cart_id. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    /** 담은 상품 (N:1). FK 컬럼은 product_id. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    /** 담은 수량 (1 이상). */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Builder
    private CartItemEntity(CartEntity cart, ProductEntity product, int quantity) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
    }

    /** 장바구니 항목을 생성한다. */
    public static CartItemEntity of(CartEntity cart, ProductEntity product, int quantity) {
        validateQuantity(quantity);
        return CartItemEntity.builder()
                .cart(cart)
                .product(product)
                .quantity(quantity)
                .build();
    }

    /** 수량을 지정 값으로 변경한다. */
    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    /** 수량을 더한다. (동일 상품 재담기) */
    public void addQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity += quantity;
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CART_QUANTITY);
        }
    }
}
