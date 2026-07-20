package org.example.groommvp.domain.order.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 상태")
public enum OrderStatus {
    PENDING_PAYMENT, // 주문은 생성되었으나, 결제는 하지 않은 상태
    COMPLETED, // 결제 성공 후 주문이 완료된 상태
    CANCELED, // 사용자가 취소한 상태
    PAYMENT_FAILED; // 결제 시도는 했지만 실패한 상태

    // 취소 가능 여부 (COMPLETED 또는 PENDING_PAYMENT일 때 가능)
    public boolean isCancelable() {
        return this == COMPLETED || this == PENDING_PAYMENT;
    }

    // 이미 취소됐는지 여부
    public boolean isCanceled() {
        return this == CANCELED;
    }

    // 결제 대기 상태(PENDING_PAYMENT)인지 확인 (결제 실패(PAYMENT_FAILED)는 포함하지 않음)
    public boolean isPendingPayment() {
        return this == PENDING_PAYMENT;
    }

    // 결제까지 완료된 주문인지 확인
    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
