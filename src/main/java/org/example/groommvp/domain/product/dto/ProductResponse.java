package org.example.groommvp.domain.product.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;

@Getter
@Builder
public class ProductResponse {

    private final String productName;
    private final Integer productPrice;
    private final Integer stocks;

    public static ProductResponse from(ProductEntity product, StockEntity stock) {
        return ProductResponse.builder()
                .productName(product.getProductName())
                .productPrice(product.getProductPrice())
                .stocks(stock.getStocks())
                .build();
    }
}
