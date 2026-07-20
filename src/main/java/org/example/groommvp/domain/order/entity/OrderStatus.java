package org.example.groommvp.domain.order.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 상태")
public enum OrderStatus {
    PENDING_PAYMENT, // 주문은 생성되었으나, 결제는 하지 않은 상태
    COMPLETED, // 결제 성공 후 주문이 완료된 상태
    CANCELED, // 사용자가 취소한 상태
    PAYMENT_FAILED; // 결제 시도는 했지만 실패한 상태

    // 취소 가능 여부 (COMPLETED일 때만 가능)
    public boolean isCancelable() {
        return this == COMPLETED || this == PENDING_PAYMENT;
    }

    // 이미 취소됐는지 여부
    public boolean isCanceled() {
        return this == CANCELED;
    }

    // 아직 결제가 완료되지 않은 주문인지 확인
    public boolean isPendingPayment() {
        return this == PENDING_PAYMENT;
    }

    // 결제까지 완료된 주문인지 확인
    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
