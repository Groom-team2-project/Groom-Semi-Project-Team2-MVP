package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 담기 요청 DTO.
 *
 * <pre>
 * POST /api/v1/carts/items
 * { "productId": 1, "quantity": 2 }
 * </pre>
 */
@Schema(description = "장바구니 담기 요청")
public record CartItemAddRequest(
        @Schema(description = "담을 상품 ID", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @Schema(description = "담을 수량 (1 이상)", example = "2",
                minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
