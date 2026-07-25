package org.example.groommvp.domain.category.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;

@Getter
@Builder
public class ProductChildResponse {

    private final Long productId;
    private final String productName;

    public static ProductChildResponse from(ProductEntity product) {
        return ProductChildResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .build();
    }
}
