package org.example.groommvp.domain.order.entity;

import java.time.LocalDateTime;

import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public Order(Long totalPrice) {
        this(totalPrice, OrderStatus.COMPLETED);
    }

    private Order(Long totalPrice, OrderStatus status) {
        if (totalPrice == null) {
            throw new IllegalArgumentException("주문 금액은 필수입니다.");
        }

        if (totalPrice < 0) {
            throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다.");
        }
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public static Order pendingPayment(Long totalPrice) {
        return new Order(totalPrice, OrderStatus.PENDING_PAYMENT);
    }

    public void completePayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PENDING);
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void failPayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PENDING);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (status.isCanceled()) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELED);
        }
        
        if (!status.isCancelable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }

        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
