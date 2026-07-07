package org.example.groommvp.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;

@Schema(description = "상품 상세 응답")
@Getter
@Builder
public class ProductResponse {

    @Schema(description = "등록된 상품명", example = "MacBook Pro")
    private final String productName;
    @Schema(description = "상품 판매 가격 (단위: 원)", example = "2500000")
    private final Integer productPrice;
    @Schema(description = "현재 재고 수량", example = "50")
    private final Integer stocks;

    public static ProductResponse from(ProductEntity product, StockEntity stock) {
        return ProductResponse.builder()
                .productName(product.getProductName())
                .productPrice(product.getProductPrice())
                .stocks(stock.getStocks())
                .build();
    }
}
