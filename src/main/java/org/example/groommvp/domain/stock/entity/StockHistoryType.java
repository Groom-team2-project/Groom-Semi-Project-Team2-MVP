package org.example.groommvp.domain.stock.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재고 변경 타입. (SoldOut 프로젝트 개요 §11)
 *
 * <p>재고가 어떤 이유로 변경되었는지를 구분한다. 변경 수량(changed_qty)의 증감 방향도
 * 이 타입으로 판단한다. (INBOUND/RESTORE = 증가, DECREASE = 감소)
 */
@Schema(description = "재고 변경 타입")
public enum StockHistoryType {

    /** 상품 입고로 인한 재고 증가 */
    INBOUND,

    /** 구매 요청으로 인한 재고 차감 */
    DECREASE,

    /** 주문 취소로 인한 재고 복구 */
    RESTORE,

    /* 결제 전 재고를 임시로 잡아둠 */
    RESERVE,

    /* 결제 성공 후 실제 재고 차감 */
    CONFIRM,

    /* 결제 실패 후 예약 재고 해제 */
    RELEASE,
}
