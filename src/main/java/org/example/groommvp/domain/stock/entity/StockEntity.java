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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 상품의 현재 재고를 보관하는 엔티티. (테이블: stocks)
 *
 * <p>상품 1건당 재고 1건(1:1)이며, FK 컬럼은 {@code product_id}.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case 로 자동 변환된다.
 * (Hibernate 기본 물리 네이밍 전략 — 예: {@code createdAt} → 컬럼 {@code created_at})
 */
@Entity
@Getter
@Table(name = "stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    /** 재고가 속한 상품 (1:1). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private ProductEntity product;

    /** 현재 재고 수량. (컬럼: stocks) */
    @Column(nullable = false)
    private int stocks;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public StockEntity(ProductEntity product, int stocks) {
        this.product = product;
        this.stocks = stocks;
    }

    /** 상품의 최초 재고(0개) 레코드를 생성한다. */
    public static StockEntity init(ProductEntity product) {
        return StockEntity.builder().product(product).stocks(0).build();
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
        if (stocks < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stocks -= quantity;
    }
}
