package org.example.groommvp.domain.stock.dto;

import java.time.LocalDateTime;
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
public class StockResponse {

    private final Long productId;
    private final String productName;
    private final int stocks;
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
