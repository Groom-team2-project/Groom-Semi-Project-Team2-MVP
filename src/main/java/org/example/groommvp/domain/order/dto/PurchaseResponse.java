package org.example.groommvp.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상품 주문 생성 및 재고 예약 응답")
public record PurchaseResponse(
        @Schema(description = "생성된 주문 ID", example = "42")
        Long orderId,
        @Schema(description = "구매한 상품 ID", example = "1")
        Long productId,
        @Schema(description = "구매 수량", example = "3")
        int purchasedQuantity,
        @Schema(description = "예약 후 판매 가능 재고 수량", example = "47")
        int remainingStockQuantity,
        @Schema(type = "string", description = "주문 생성 시각 (서버 로컬 시간 기준 ISO-8601 LocalDateTime 형식, 예: 2024-01-15T10:30:00)", example = "2024-01-15T10:30:00")
        LocalDateTime orderedAt
) {
}
