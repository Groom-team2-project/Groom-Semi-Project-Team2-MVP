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

    @Column(name = "member_id")
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public Order(Long totalPrice) {
        this(null, totalPrice, OrderStatus.COMPLETED);
    }

    public Order(Long memberId, Long totalPrice) {
        this(memberId, totalPrice, OrderStatus.COMPLETED);
    }

    private Order(Long memberId, Long totalPrice, OrderStatus status) {
        if (totalPrice == null) {
            throw new IllegalArgumentException("주문 금액은 필수입니다.");
        }

        if (totalPrice < 0) {
            throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다.");
        }
        this.memberId = memberId;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public static Order pendingPayment(
            Long memberId,
            Long totalPrice,
            LocalDateTime paymentExpiresAt
    ) {
        Order order = new Order(
                memberId,
                totalPrice,
                OrderStatus.PENDING_PAYMENT
        );
        order.paymentExpiresAt = paymentExpiresAt;
        return order;
    }

    public static Order pendingPayment(Long totalPrice) {
        return pendingPayment(null, totalPrice);
    }

    public static Order pendingPayment(Long memberId, Long totalPrice) {
        return new Order(memberId, totalPrice, OrderStatus.PENDING_PAYMENT);
    }

    /**
     * 결제 승인 요청을 시작한다. (PENDING_PAYMENT → PAYMENT_PROCESSING)
     *
     * <p>이 상태로 바꿔두면 예약 만료 스케줄러가 해당 주문을 건드리지 않는다.
     * 외부 승인 요청 중에 예약이 해제되어 "토스는 결제 성공, 주문은 취소"가 되는 것을 막는다.
     */
    public void startPayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PENDING);
        }
        this.status = OrderStatus.PAYMENT_PROCESSING;
    }

    /** 결제 승인 성공. (PAYMENT_PROCESSING → COMPLETED) */
    public void completePayment() {
        if (this.status != OrderStatus.PAYMENT_PROCESSING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PROCESSING);
        }
        this.status = OrderStatus.COMPLETED;
    }

    /**
     * 결제 시도가 실패했으나 주문은 살려둔다. (PAYMENT_PROCESSING → PENDING_PAYMENT)
     *
     * <p>카드 거절이나 사용자의 결제창 취소는 "이 시도만 실패"한 것이므로 주문을 취소하지 않는다.
     * 예약을 유지해 만료 시각 전까지 다른 결제 수단으로 재시도할 수 있게 한다.
     */
    public void revertToPendingPayment() {
        if (this.status != OrderStatus.PAYMENT_PROCESSING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PROCESSING);
        }
        this.status = OrderStatus.PENDING_PAYMENT;
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
