package org.example.groommvp.domain.order.entity;

public enum OrderStatus {
    COMPLETED, // 구매 완료
    CANCELED; // 취소됨

    // 취소 가능 여부 (COMPLETED일 때만 가능)
    public boolean isCancelable() {
        return this == COMPLETED;
    }

    // 이미 취소됐는지 여부
    public boolean isCanceled() {
        return this == CANCELED;
    }
}
