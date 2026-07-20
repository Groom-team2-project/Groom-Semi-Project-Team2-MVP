package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.groommvp.domain.cart.entity.CartItemEntity;

/**
 * 장바구니 항목 응답 DTO.
 *
 * <p>{@link CartResponse} 와 함께 Redis 캐시에 JSON 으로 직렬화된다. record 는 Jackson 이
 * 별도 설정 없이 역직렬화할 수 있어 캐시 값 타입으로 안전하다.
 */
@Schema(description = "장바구니 항목")
public record CartItemResponse(
        @Schema(description = "장바구니 항목 ID", example = "10")
        Long cartItemId,
        @Schema(description = "상품 ID", example = "1")
        Long productId,
        @Schema(description = "상품명", example = "MacBook Pro")
        String productName,
        @Schema(description = "상품 단가", example = "1000")
        int productPrice,
        @Schema(description = "담은 수량", example = "2")
        int quantity,
        @Schema(description = "항목 합계 (단가 × 수량)", example = "2000")
        int lineTotal
) {

    public static CartItemResponse from(CartItemEntity item) {
        int price = item.getProduct().getProductPrice();
        return new CartItemResponse(
                item.getCartItemId(),
                item.getProduct().getProductId(),
                item.getProduct().getProductName(),
                price,
                item.getQuantity(),
                price * item.getQuantity()
        );
    }
}
