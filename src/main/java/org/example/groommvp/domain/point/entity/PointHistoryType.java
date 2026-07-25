package org.example.groommvp.domain.point.entity;

/**
 * 포인트 변동 타입.
 *
 * <p>증감 방향을 타입이 결정한다. (적립/취소복구는 +, 사용은 -)
 */
public enum PointHistoryType {

    /** 적립. (구매 확정 등) */
    EARN,

    /** 사용. (주문 결제 시 차감) */
    USE,

    /** 사용 취소 복구. (주문 취소/결제 실패로 사용했던 포인트를 되돌림) */
    CANCEL
}
