package org.example.groommvp.domain.cancel.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 복구된 품목 (어떤 상품을 몇개 복구 했나)
@Schema(description = "취소로 인해 재고가 복구된 품목")
public record RestoredItemResponse (
    @Schema(description = "복구된 상품 ID", example = "1")
    Long productId,
    @Schema(description = "복구된 수량", example = "3")
    int quantity
) {}
