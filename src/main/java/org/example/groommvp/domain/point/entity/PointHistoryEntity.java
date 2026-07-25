package org.example.groommvp.domain.point.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.global.entity.BaseEntity;

/**
 * 포인트 변동 이력. (테이블: point_histories)
 *
 * <p>적립/사용/취소가 일어날 때마다 한 행씩 적재되어 "어떤 회원이, 어떤 타입으로, 몇 점,
 * 어떤 주문 때문에, 변동 후 잔액이 얼마인지"를 추적한다.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case. (팀 컨벤션)
 *
 * <p><b>멱등성 제약:</b> {@code (member_id, order_id, type)} 유니크 제약으로, 같은 주문에 대한
 * 같은 타입 변동이 두 번 적재되지 않게 한다. 특히 주문 취소(CANCEL) 이벤트가 재전송돼도
 * 복구가 한 번만 반영되도록 하는 최종 방어선이다. (order_id 가 null 인 행은 제약 대상이 아니다)
 */
@Entity
@Getter
@Table(
        name = "point_histories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_POINT_HISTORIES_MEMBER_ORDER_TYPE",
                        columnNames = {"member_id", "order_id", "type"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistoryEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_history_id")
    private Long pointHistoryId;

    /** 변동이 일어난 회원 (N:1). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    /** 변동 타입 (적립/사용/취소). 증감 방향을 결정한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PointHistoryType type;

    /** 변동 금액 (양수 magnitude). 방향은 {@code type} 으로 구분한다. */
    @Column(name = "amount", nullable = false)
    private long amount;

    /** 변동 후 잔액 (스냅샷). 이력만 봐도 잔액 추이를 알 수 있다. */
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    /** 변동을 유발한 주문 ID (선택). 적립/사용이 주문과 무관하면 null. */
    @Column(name = "order_id")
    private Long orderId;

    @Builder
    private PointHistoryEntity(MemberEntity member, PointHistoryType type, long amount,
                               long balanceAfter, Long orderId) {
        this.member = member;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.orderId = orderId;
    }

    public static PointHistoryEntity of(MemberEntity member, PointHistoryType type, long amount,
                                        long balanceAfter, Long orderId) {
        return PointHistoryEntity.builder()
                .member(member)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .orderId(orderId)
                .build();
    }
}
