package org.example.groommvp.domain.stock.dto;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.stock.entity.StockEntity;

/**
 * 현재 재고 조회 응답 DTO.
 *
 * <pre>
 * GET /api/v1/products/{productId}/stock
 * { "productId": 1, "productName": "티셔츠", "stocks": 15, "updatedAt": "..." }
 * </pre>
 */
@Getter
@Builder
@Schema(description = "현재 재고 조회 응답")
public class StockResponse {

    @Schema(description = "상품 ID", example = "1")
    private final Long productId;
    @Schema(description = "상품명", example = "MacBook Pro")
    private final String productName;
    @Schema(description = "현재 재고 수량", example = "50")
    private final int stocks;
    @Schema(type = "string", description = "재고 수정 시각 (서버 로컬 시간 기준 ISO-8601 LocalDateTime 형식, 예: 2024-01-15T09:00:00)", example = "2024-01-15T09:00:00")
    private final LocalDateTime updatedAt;

    public static StockResponse from(StockEntity stock) {
        return StockResponse.builder()
                .productId(stock.getProduct().getProductId())
                .productName(stock.getProduct().getProductName())
                .stocks(stock.getStocks())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }
}
