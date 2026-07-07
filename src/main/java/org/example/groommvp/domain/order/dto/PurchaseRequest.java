package org.example.groommvp.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "상품 구매 요청")
public record PurchaseRequest(
        @Schema(description = "구매 수량 (1 이상)", example = "3",
                minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "결제 수량은 필수입니다.")
        @Min(value = 1, message = "결제 수량은 적어도 1개 이상이어야 합니다.")
        Integer quantity
) {
}
