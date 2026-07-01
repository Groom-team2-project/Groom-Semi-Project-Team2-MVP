package org.example.groommvp.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;

@Getter
@Builder
@JsonPropertyOrder({"productId", "productName", "productPrice", "status", "stockQuantity"})
public class ProductListResponse {
    private Long productId;
    private String productName;
    private int productPrice;
    //상품상태 추가 필요

    public static ProductListResponse from(ProductEntity product){
        return ProductListResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productPrice(product.getProductPrice())
                .build();
    }

}
