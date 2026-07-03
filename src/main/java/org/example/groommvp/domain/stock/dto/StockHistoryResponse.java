package org.example.groommvp.domain.stock.dto;

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
@Getter
@Builder
public class StockHistoryResponse {

    private final Long historyId;
    private final Long stockId;
    private final Long productId;
    private final String productName;
    private final Long orderId;
    private final StockHistoryType type;
    private final int changedQty;
    private final int currentStocks;
    private final String reason;
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
