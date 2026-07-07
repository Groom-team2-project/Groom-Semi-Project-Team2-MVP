package org.example.groommvp.domain.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 입고 요청 DTO.
 *
 * <pre>
 * POST /api/v1/products/{productId}/stock-in
 * { "quantity": 10, "reason": "정기 입고" }
 * </pre>
 */
@Schema(description = "재고 입고 요청")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockInRequest {

    @Schema(description = "입고 수량 (1 이상)", example = "30",
            minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "입고 수량은 필수입니다.")
    @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    /** 변경 사유 (선택). stock_history.reason 에 기록된다. */
    @Schema(description = "입고 사유 (선택, 미입력 시 null)", example = "정기 입고", nullable = true)
    private String reason;
}
