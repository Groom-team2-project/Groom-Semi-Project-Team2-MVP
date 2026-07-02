package org.example.groommvp.domain.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 입고 요청 DTO.
 *
 * <pre>
 * POST /api/products/{productId}/stock-in
 * { "quantity": 10, "reason": "정기 입고" }
 * </pre>
 */
@Getter
@NoArgsConstructor
public class StockInRequest {

    @NotNull(message = "입고 수량은 필수입니다.")
    @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    /** 변경 사유 (선택). stock_history.reason 에 기록된다. */
    private String reason;
}
