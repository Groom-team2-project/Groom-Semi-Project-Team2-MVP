package org.example.groommvp.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;

@Schema(description = "상품 목록 응답")
@Getter
@Builder
@JsonPropertyOrder({"productId", "productName", "productPrice", "stocks", "viewCount"})
public class ProductListResponse {
    @Schema(description = "상품 ID", example = "1")
    private Long productId;
    @Schema(description = "상품명", example = "MacBook Pro")
    private String productName;
    @Schema(description = "상품 가격", example = "2500000")
    private int productPrice;
    @Schema(description = "재고 수량", example = "50")
    private int stocks;
    @Schema(description = "조회수", example = "42")
    private Long viewCount;
    public static ProductListResponse from(ProductEntity product, Integer stocks){
        return ProductListResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productPrice(product.getProductPrice())
                .stocks(stocks != null ? stocks : 0)
                .viewCount(product.getViewCount())
                .build();
    }
}