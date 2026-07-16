package org.example.groommvp.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockEntityReservationTest {

    @Test
    @DisplayName("재고를 예약하면 예약 재고가 증가하고 판매 가능 재고가 감소합니다")
    void reserve_decreasesAvailableStocks() {
        ProductEntity product = ProductEntity.builder()
                .productName("테스트 상품")
                .productPrice(10000)
                .build();
        StockEntity stock = StockEntity.builder()
                .product(product)
                .stocks(10)
                .build();

        stock.reserve(3);

        assertThat(stock.getStocks()).isEqualTo(10);
        assertThat(stock.getReservedStocks()).isEqualTo(3);
        assertThat(stock.getAvailableStocks()).isEqualTo(7);
    }

    @Test
    @DisplayName("예약 재고를 확정하면 예약 재고와 실제 재고가 함께 감소합니다")
    void confirm_decreasesReservedAndStocks() {
        ProductEntity product = ProductEntity.builder()
                .productName("테스트 상품")
                .productPrice(10000)
                .build();
        StockEntity stock = StockEntity.builder()
                .product(product)
                .stocks(10)
                .build();
        stock.reserve(3);

        stock.confirm(3);

        assertThat(stock.getStocks()).isEqualTo(7);
        assertThat(stock.getReservedStocks()).isEqualTo(0);
        assertThat(stock.getAvailableStocks()).isEqualTo(7);
    }

    @Test
    @DisplayName("예약 재고를 해제하면 예약 재고만 감소하고 실제 재고는 유지됩니다")
    void release_decreasesOnlyReservedStocks() {
        ProductEntity product = ProductEntity.builder()
                .productName("테스트 상품")
                .productPrice(10000)
                .build();
        StockEntity stock = StockEntity.builder()
                .product(product)
                .stocks(10)
                .build();
        stock.reserve(3);

        stock.release(3);

        assertThat(stock.getStocks()).isEqualTo(10);
        assertThat(stock.getReservedStocks()).isEqualTo(0);
        assertThat(stock.getAvailableStocks()).isEqualTo(10);
    }

    @Test
    @DisplayName("판매 가능 재고보다 많이 예약하면 OUT_OF_STOCK 예외가 발생합니다")
    void reserve_overAvailableStocks_throwsException() {
        ProductEntity product = ProductEntity.builder()
                .productName("테스트 상품")
                .productPrice(10000)
                .build();
        StockEntity stock = StockEntity.builder()
                .product(product)
                .stocks(10)
                .build();

        assertThatThrownBy(() -> stock.reserve(11))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
    }
}
