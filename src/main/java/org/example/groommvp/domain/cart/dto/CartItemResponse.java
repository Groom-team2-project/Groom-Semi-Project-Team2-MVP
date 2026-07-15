package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.cart.entity.CartItemEntity;

/**
 * 장바구니 항목 응답 DTO.
 */
@Getter
@Builder
@Schema(description = "장바구니 항목")
public class CartItemResponse {

    @Schema(description = "장바구니 항목 ID", example = "10")
    private final Long cartItemId;
    @Schema(description = "상품 ID", example = "1")
    private final Long productId;
    @Schema(description = "상품명", example = "MacBook Pro")
    private final String productName;
    @Schema(description = "상품 단가", example = "1000")
    private final int productPrice;
    @Schema(description = "담은 수량", example = "2")
    private final int quantity;
    @Schema(description = "항목 합계 (단가 × 수량)", example = "2000")
    private final int lineTotal;

    public static CartItemResponse from(CartItemEntity item) {
        int price = item.getProduct().getProductPrice();
        return CartItemResponse.builder()
                .cartItemId(item.getCartItemId())
                .productId(item.getProduct().getProductId())
                .productName(item.getProduct().getProductName())
                .productPrice(price)
                .quantity(item.getQuantity())
                .lineTotal(price * item.getQuantity())
                .build();
    }
}
