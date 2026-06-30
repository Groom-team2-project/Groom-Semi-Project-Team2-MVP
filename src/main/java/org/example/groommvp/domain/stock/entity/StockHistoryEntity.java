package org.example.groommvp.domain.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 재고 변동 히스토리 엔티티. (ERD: stock_history)
 *
 * <p>입고/출고 등 재고가 변할 때마다 한 행씩 적재되어 "어떤 재고가, 몇 개, 어떤 사유로,
 * (주문에 의한 변동이면) 어떤 주문 때문에" 변동되었는지를 추적한다.
 *
 * <p><b>연관관계:</b>
 * <ul>
 *   <li>{@link StockEntity} 와 다대일(N:1). FK {@code stock_id} (NOT NULL).</li>
 *   <li>주문({@code orders}) 과 다대일(N:1, 선택). FK {@code order_id} (NULL). 주문 도메인이 아직 없어
 *       지금은 nullable {@code Long} 컬럼으로 보관하고, 주문 엔티티가 생기면 연관관계로 승격한다.</li>
 * </ul>
 * 이력은 변하지 않으므로 생성 시각만 기록한다.
 */
@Entity
@Getter
@Table(name = "stock_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long history_id;

    /** 변동이 일어난 재고 (N:1). FK 컬럼은 stock_id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private StockEntity stock;

    /** 변동을 유발한 주문 ID (선택). 입고처럼 주문과 무관한 변동은 null. */
    @Column(name = "order_id")
    private Long order_id;

    /** 변경 사유 (예: "정기 입고", "주문 출고"). 선택값. */
    @Column(length = 50)
    private String reason;

    /** 변경 수량. 입고는 양수, 출고는 음수로 기록한다. */
    @Column(nullable = false)
    private int changed_qty;

    @CreationTimestamp
    private LocalDateTime created_at;

    @Builder
    private StockHistoryEntity(StockEntity stock, Long order_id, String reason, int changed_qty) {
        this.stock = stock;
        this.order_id = order_id;
        this.reason = reason;
        this.changed_qty = changed_qty;
    }

    /**
     * 입고 히스토리를 생성하는 정적 팩토리 메서드. (주문과 무관하므로 order_id 는 null)
     *
     * @param stock      입고된 재고
     * @param quantity   입고 수량 (양수)
     * @param reason     변경 사유 (nullable)
     */
    public static StockHistoryEntity inbound(StockEntity stock, int quantity, String reason) {
        return StockHistoryEntity.builder()
                .stock(stock)
                .order_id(null)
                .reason(reason)
                .changed_qty(quantity)
                .build();
    }
}
