package org.example.groommvp.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;

import java.util.List;

@Schema(description = "상품 상세 응답")
@Getter
@Builder
public class ProductDetailResponse {

    @Schema(description = "등록된 상품명", example = "MacBook Pro")
    private final String productName;

    @Schema(description = "상품 판매 가격 (단위: 원)", example = "2500000")
    private final Integer productPrice;

    @Schema(description = "상품 이미지", example = "https://example.com/product.jpg",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String productImage;

    @Schema(description = "현재 재고 수량", example = "50")
    private final Integer stocks;

    @Schema(description = "등록 카테고리", example = "2")
    private final Long category;

    private final List<ImageResponse> detailImages;

    public static ProductDetailResponse from(
            ProductEntity product,
            StockEntity stock,
            String productImageUrl,
            List<ImageResponse> detailImages
    ) {
        return ProductDetailResponse.builder()
                .productName(product.getProductName())
                .productPrice(product.getProductPrice())
                .productImage(productImageUrl)
                .stocks(stock.getStocks())
                .category(product.getCategory().getCategoryId())
                .detailImages(detailImages)
                .build();
    }
}
