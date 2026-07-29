package org.example.groommvp.domain.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductListResponseTest {

    @Test
    @DisplayName("상품 목록 응답은 예약 재고를 제외한 구매 가능 재고를 포함한다")
    void from_includesAvailableStocks() {
        ProductEntity product = ProductEntity.builder()
                .productName("테스트 상품")
                .productPrice(10000)
                .productImage("https://example.com/product.jpg")
                .build();
        StockEntity stock = StockEntity.builder()
                .product(product)
                .stocks(10)
                .build();
        stock.reserve(3);

        ProductListResponse response = ProductListResponse.from(product, stock);

        assertThat(response.getStocks()).isEqualTo(10);
        assertThat(response.getReservedStocks()).isEqualTo(3);
        assertThat(response.getAvailableStocks()).isEqualTo(7);
    }
}
