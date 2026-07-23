package org.example.groommvp.domain.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

/**
 * 발급 가능한 쿠폰(정책) 엔티티. (테이블: coupons)
 *
 * <p>회원이 실제로 보유하는 쿠폰은 {@link MemberCouponEntity} 이고, 이 엔티티는
 * "어떤 할인을, 몇 장까지, 언제까지 발급할 수 있는가"를 정의한다.
 *
 * <p><b>수량 제어:</b> {@code issuedQuantity} 를 증가시키는 방식으로 선착순 수량을 관리한다.
 * 동시 발급 경합은 리포지토리의 비관적 락으로 직렬화한다.
 * ({@code CouponRepository#findByIdWithPessimisticLock})
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case. (팀 컨벤션)
 */
@Entity
@Getter
@Table(name = "coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_name", nullable = false, length = 100)
    private String couponName;

    /** 할인 방식 (정액/정률). 할인 계산 규칙은 {@link DiscountType} 이 갖는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** 정액이면 금액(원), 정률이면 비율(%). */
    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    /** 정률 할인의 최대 할인 금액. 정액이면 무의미하므로 null 허용. */
    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    /** 이 쿠폰을 쓰기 위한 최소 주문 금액. */
    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    /** 총 발급 가능 수량. */
    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    /** 현재까지 발급된 수량. */
    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    /** 발급 시작 시각. */
    @Column(name = "issue_start_at", nullable = false)
    private LocalDateTime issueStartAt;

    /** 발급 종료 시각. */
    @Column(name = "issue_end_at", nullable = false)
    private LocalDateTime issueEndAt;

    /** 발급일로부터 사용 가능한 일수. 회원 쿠폰의 만료일 계산에 쓰인다. */
    @Column(name = "valid_days", nullable = false)
    private int validDays;

    @Builder
    private CouponEntity(String couponName, DiscountType discountType, int discountValue,
                         Integer maxDiscountAmount, int minOrderAmount, int totalQuantity,
                         LocalDateTime issueStartAt, LocalDateTime issueEndAt, int validDays) {
        this.couponName = couponName;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = minOrderAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.issueStartAt = issueStartAt;
        this.issueEndAt = issueEndAt;
        this.validDays = validDays;
    }

    /** 남은 발급 가능 수량. */
    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity;
    }

    /**
     * 발급 수량을 1 증가시킨다.
     *
     * <p>발급 기간과 잔여 수량을 함께 검증한다. 수량 검증이 이 메서드 안에 있어야
     * 비관적 락으로 직렬화된 구간에서 초과 발급이 발생하지 않는다.
     *
     * @param now 발급 시각
     * @throws BusinessException 발급 기간이 아니거나 수량이 소진된 경우
     */
    public void issue(LocalDateTime now) {
        if (now.isBefore(issueStartAt) || now.isAfter(issueEndAt)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_ISSUABLE);
        }
        if (issuedQuantity >= totalQuantity) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }
        this.issuedQuantity++;
    }

    /**
     * 주문 금액에 대한 할인액을 계산한다.
     *
     * @throws BusinessException 최소 주문 금액을 충족하지 않는 경우
     */
    public long calculateDiscount(long orderAmount) {
        if (orderAmount < minOrderAmount) {
            throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_NOT_MET);
        }
        return discountType.calculateDiscount(orderAmount, discountValue, maxDiscountAmount);
    }
}
