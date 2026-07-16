package org.example.groommvp.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

/**
 * 상품의 현재 재고를 보관하는 엔티티. (테이블: stocks)
 *
 * <p>상품 1건당 재고 1건(1:1)이며, FK 컬럼은 {@code product_id}.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case 이며,
 * 컬럼명은 {@code @Column(name = "...")} 으로 명시한다. (팀 컨벤션)
 */
@Entity
@Getter
@Table(name = "stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    /** 재고가 속한 상품 (1:1). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private ProductEntity product;

    /** 현재 재고 수량. */
    @Column(name = "stocks", nullable = false)
    private int stocks;

    /** 결제 대기 중인 수량 **/
    @Column(name = "reserved_stocks", nullable = false)
    private int reservedStocks;

    @Builder
    public StockEntity(ProductEntity product, int stocks) {
        this.product = product;
        this.stocks = stocks;
        this.reservedStocks = 0;
    }

    /** 상품의 최초 재고(0개) 레코드를 생성한다. */
    public static StockEntity init(ProductEntity product) {
        return StockEntity.builder().product(product).stocks(0).build();
    }

    // 실제 보유 재고 - 결제 대기 중 잡아둔 재고 = 새로 판매 가능한 재고
    public int getAvailableStocks() {
        return this.stocks - this.reservedStocks;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            // 0개 또는 음수는 예약할 수 없으므로 잘못된 요청
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        if (getAvailableStocks() < quantity) {
            // 지금 팔 수 있는 재고보다 많이 예약하려 하면 품절 처리
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.reservedStocks += quantity; // 실제 재고 stocks는 그대로 두고, 예약 재고만 증가
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        if (this.reservedStocks < quantity) {
            // 예약된 수량보다 더 많이 확정할 수는 없음
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        this.reservedStocks -= quantity;
        this.stocks -= quantity; // 실제로 판매되면 실제 재고도 줄임
    }

    // 결제 실패 또는 시간 초과로 예약된 재고를 다시 판매 가능 상태로 풀어주는 기능
    public void release(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        if (this.reservedStocks < quantity) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        this.reservedStocks -= quantity; // 예약 수량만 줄임
    }

    /**
     * 재고를 증가시킨다. (입고)
     *
     * @param quantity 증가시킬 수량 (1 이상)
     * @throws BusinessException 수량이 0 이하인 경우
     */
    public void increase(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        this.stocks += quantity;
    }

    public void decrease(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
        if (getAvailableStocks() < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stocks -= quantity;
    }
}
