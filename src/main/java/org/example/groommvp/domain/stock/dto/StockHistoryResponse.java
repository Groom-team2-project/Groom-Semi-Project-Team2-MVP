package org.example.groommvp.domain.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryType;

/**
 * 재고 변동 히스토리 응답 DTO. (ERD: stock_history 기준)
 *
 * <p>엔티티를 그대로 노출하지 않고 필요한 값만 추려서 응답한다.
 * 어떤 재고/상품의 변동인지 파악할 수 있도록 stock 을 통해 상품 정보도 함께 담는다.
 */
@Schema(description = "재고 변동 이력 응답")
@Getter
@Builder
public class StockHistoryResponse {

    @Schema(description = "재고 변동 이력 고유 식별자", example = "10")
    private final Long historyId;
    @Schema(description = "재고 레코드 고유 식별자", example = "1")
    private final Long stockId;
    @Schema(description = "상품 ID", example = "1")
    private final Long productId;
    @Schema(description = "상품명", example = "MacBook Pro")
    private final String productName;
    @Schema(description = "관련 주문 ID (입고 시 null)", example = "42", nullable = true)
    private final Long orderId;
    @Schema(description = "변동 타입 (INBOUND: 입고, DECREASE: 구매 차감, RESTORE: 취소 복구)", example = "INBOUND")
    private final StockHistoryType type;
    @Schema(description = "변동 수량", example = "30")
    private final int changedQty;
    @Schema(description = "변동 후 현재 재고 수량", example = "80")
    private final int currentStocks;
    @Schema(description = "변동 사유 (선택)", example = "정기 입고", nullable = true)
    private final String reason;
    @Schema(type = "string", description = "변동 발생 시각 (서버 로컬 시간 기준 ISO-8601 LocalDateTime 형식, 예: 2024-01-15T09:00:00)", example = "2024-01-15T09:00:00")
    private final LocalDateTime createdAt;

    public static StockHistoryResponse from(StockHistoryEntity history) {
        StockEntity stock = history.getStock();
        return StockHistoryResponse.builder()
                .historyId(history.getHistoryId())
                .stockId(stock.getStockId())
                .productId(stock.getProduct().getProductId())
                .productName(stock.getProduct().getProductName())
                .orderId(history.getOrderId())
                .type(history.getChangeType())
                .changedQty(history.getChangedQty())
                .currentStocks(stock.getStocks())
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
