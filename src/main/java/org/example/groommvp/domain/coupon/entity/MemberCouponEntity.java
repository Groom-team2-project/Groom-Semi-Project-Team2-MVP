package org.example.groommvp.domain.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

/**
 * 회원이 보유한 쿠폰. (테이블: member_coupons)
 *
 * <p><b>중복 발급 방지:</b> {@code (member_id, coupon_id)} 유니크 제약으로 DB 차원에서 막는다.
 * 애플리케이션 검증만으로는 동시 요청에서 뚫리므로 제약을 함께 건다.
 *
 * <p><b>중복 사용 방지:</b> {@link #use} 가 이미 사용된 쿠폰이면 예외를 던진다.
 * 사용 여부는 {@code usedAt} 하나로 표현해 "사용됨 + 사용시각"이 어긋날 수 없게 한다.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case. (팀 컨벤션)
 */
@Entity
@Getter
@Table(
        name = "member_coupons",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_MEMBER_COUPONS_MEMBER_COUPON",
                        columnNames = {"member_id", "coupon_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCouponEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_coupon_id")
    private Long memberCouponId;

    /** 쿠폰을 보유한 회원 (N:1). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    /** 발급받은 쿠폰 정책 (N:1). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private CouponEntity coupon;

    /** 사용 시각. null 이면 미사용. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 사용 만료 시각. 발급 시각 + 쿠폰의 validDays. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 사용된 주문 ID. 주문 도메인과의 결합을 피하려고 FK 없이 값만 보관한다. */
    @Column(name = "used_order_id")
    private Long usedOrderId;

    @Builder
    private MemberCouponEntity(MemberEntity member, CouponEntity coupon, LocalDateTime expiresAt) {
        this.member = member;
        this.coupon = coupon;
        this.expiresAt = expiresAt;
    }

    /** 회원에게 쿠폰을 발급한다. 만료일은 쿠폰의 {@code validDays} 로 계산한다. */
    public static MemberCouponEntity issue(MemberEntity member, CouponEntity coupon, LocalDateTime now) {
        return MemberCouponEntity.builder()
                .member(member)
                .coupon(coupon)
                .expiresAt(now.plusDays(coupon.getValidDays()))
                .build();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    /** 이 쿠폰의 소유자인지 확인한다. */
    public boolean isOwnedBy(Long memberId) {
        return this.member.getMemberId().equals(memberId);
    }

    /** 지금 사용할 수 있는 상태인지. (미사용 + 미만료) */
    public boolean isUsable(LocalDateTime now) {
        return !isUsed() && !isExpired(now);
    }

    /**
     * 쿠폰을 사용 처리하고 할인 금액을 반환한다.
     *
     * @param orderAmount 할인 적용 대상 주문 금액
     * @param orderId     사용된 주문 ID
     * @param now         사용 시각
     * @return 할인 금액
     * @throws BusinessException 이미 사용했거나, 만료됐거나, 최소 주문 금액 미달인 경우
     */
    public long use(long orderAmount, Long orderId, LocalDateTime now) {
        if (isUsed()) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        long discount = coupon.calculateDiscount(orderAmount);
        this.usedAt = now;
        this.usedOrderId = orderId;
        return discount;
    }

    /** 주문 취소/결제 실패 시 사용을 되돌린다. */
    public void cancelUse() {
        this.usedAt = null;
        this.usedOrderId = null;
    }
}
