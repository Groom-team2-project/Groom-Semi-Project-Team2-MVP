package org.example.groommvp.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 재고 변경 이력 엔티티. (테이블: stock_histories / SoldOut 개요 §8·§11)
 *
 * <p>입고/구매/취소 등으로 재고가 변할 때마다 한 행씩 적재되어 "어떤 재고가, 어떤 타입으로,
 * 몇 개, 어떤 사유로, (주문에 의한 변동이면) 어떤 주문 때문에" 변동되었는지를 추적한다.
 *
 * <p><b>연관관계:</b>
 * <ul>
 *   <li>{@link StockEntity} 와 다대일(N:1). FK {@code stock_id} (NOT NULL).</li>
 *   <li>주문({@code orders}) 과 다대일(N:1, 선택). FK {@code order_id} (NULL). 주문 도메인이 아직 없어
 *       지금은 nullable {@code Long} 컬럼으로 보관하고, 주문 엔티티가 생기면 연관관계로 승격한다.</li>
 * </ul>
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case 이며,
 * 컬럼명은 {@code @Column(name = "...")} 으로 명시한다. (팀 컨벤션)
 */
@Entity
@Getter
@Table(name = "stock_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    /** 변동이 일어난 재고 (N:1). FK 컬럼은 stock_id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private StockEntity stock;

    /** 변동을 유발한 주문 ID (선택). 입고처럼 주문과 무관한 변동은 null. */
    @Column(name = "order_id")
    private Long orderId;

    /** 변경 타입 (입고/차감/복구). 증감 방향을 결정한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private StockHistoryType changeType;

    /** 변경 사유 (예: "정기 입고", "주문 출고"). 선택값. */
    @Column(name = "reason", length = 50)
    private String reason;

    /** 변경 수량 (양수 magnitude). 증가/감소 방향은 {@code changeType} 으로 구분한다. */
    @Column(name = "changed_qty", nullable = false)
    private int changedQty;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private StockHistoryEntity(StockEntity stock, Long orderId, StockHistoryType changeType,
                              String reason, int changedQty) {
        this.stock = stock;
        this.orderId = orderId;
        this.changeType = changeType;
        this.reason = reason;
        this.changedQty = changedQty;
    }

    /**
     * 입고 이력 생성. (INBOUND, 주문과 무관하므로 orderId 는 null)
     *
     * @param stock    입고된 재고
     * @param quantity 입고 수량 (양수)
     * @param reason   변경 사유 (nullable)
     */
    public static StockHistoryEntity inbound(StockEntity stock, int quantity, String reason) {
        return StockHistoryEntity.builder()
                .stock(stock)
                .orderId(null)
                .changeType(StockHistoryType.INBOUND)
                .reason(reason)
                .changedQty(quantity)
                .build();
    }

    /**
     * 구매 차감 이력 생성. (DECREASE, 구매 주문에 의한 변동)
     *
     * @param stock    차감된 재고
     * @param orderId  변동을 유발한 주문 ID
     * @param quantity 차감 수량 (양수 magnitude)
     * @param reason   변경 사유 (nullable)
     */
    public static StockHistoryEntity decrease(StockEntity stock, Long orderId, int quantity, String reason) {
        return StockHistoryEntity.builder()
                .stock(stock)
                .orderId(orderId)
                .changeType(StockHistoryType.DECREASE)
                .reason(reason)
                .changedQty(quantity)
                .build();
    }

    /**
     * 취소 복구 이력 생성. (RESTORE, 주문 취소에 의한 변동)
     *
     * @param stock    복구된 재고
     * @param orderId  취소된 주문 ID
     * @param quantity 복구 수량 (양수 magnitude)
     * @param reason   변경 사유 (nullable)
     */
    public static StockHistoryEntity restore(StockEntity stock, Long orderId, int quantity, String reason) {
        return StockHistoryEntity.builder()
                .stock(stock)
                .orderId(orderId)
                .changeType(StockHistoryType.RESTORE)
                .reason(reason)
                .changedQty(quantity)
                .build();
    }
}
