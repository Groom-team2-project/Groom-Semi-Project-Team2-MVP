package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 항목 수량 변경 요청 DTO.
 *
 * <pre>
 * PATCH /api/v1/carts/items/{cartItemId}
 * { "quantity": 5 }
 * </pre>
 */
@Schema(description = "장바구니 항목 수량 변경 요청")
public record CartItemUpdateRequest(
        @Schema(description = "변경할 수량 (1 이상)", example = "5",
                minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
