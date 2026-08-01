package org.example.groommvp.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;

@Schema(description = "상품 목록 응답")
@Getter
@Builder
@JsonPropertyOrder({"productId", "productName", "productPrice", "stocks", "reservedStocks", "availableStocks", "viewCount"})
public class ProductListResponse {
    @Schema(description = "상품 ID", example = "1")
    private Long productId;
    @Schema(description = "상품명", example = "MacBook Pro")
    private String productName;
    @Schema(description = "상품 가격", example = "2500000")
    private int productPrice;
    @Schema(description = "재고 수량", example = "50")
    private int stocks;
    @Schema(description = "결제 대기 주문이 예약한 재고 수량", example = "3")
    private int reservedStocks;
    @Schema(description = "결제 대기 예약분을 제외한 구매 가능 재고 수량", example = "47")
    private int availableStocks;
    @Schema(description = "조회수", example = "42")
    private Long viewCount;
    public static ProductListResponse from(ProductEntity product, StockEntity stock){
        int stocks = stock != null ? stock.getStocks() : 0;
        int reservedStocks = stock != null ? stock.getReservedStocks() : 0;
        int availableStocks = stock != null ? stock.getAvailableStocks() : 0;

        return ProductListResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productPrice(product.getProductPrice())
                .stocks(stocks)
                .reservedStocks(reservedStocks)
                .availableStocks(availableStocks)
                .viewCount(product.getViewCount())
                .build();
    }
}
