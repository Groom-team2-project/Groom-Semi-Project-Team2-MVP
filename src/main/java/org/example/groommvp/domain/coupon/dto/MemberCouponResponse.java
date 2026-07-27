package org.example.groommvp.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;

/**
 * 회원이 보유한 쿠폰 응답 DTO.
 *
 * <p>보유 정보(사용 여부/만료일)와 쿠폰 정책(할인 조건)을 함께 담는다.
 *
 * <p><b>정책은 {@link CouponPolicy} 로 따로 담는다.</b> 어드민용 {@link CouponResponse} 를
 * 그대로 재사용하면 잔여 발급 수량({@code remainingQuantity})까지 회원에게 나간다. 그 값은
 * 운영 정보이고, 노출되면 발급 현황을 외부에서 폴링해 추적할 수 있다. 이미 발급받은 회원에게는
 * 필요하지도 않으므로 회원 응답에서는 뺀다.
 */
@Schema(description = "보유 쿠폰 정보")
public record MemberCouponResponse(
        @Schema(description = "보유 쿠폰 ID (주문 시 이 ID로 쿠폰을 지정한다)", example = "10")
        Long memberCouponId,
        @Schema(description = "쿠폰 정책")
        CouponPolicy coupon,
        @Schema(description = "사용 여부", example = "false")
        boolean used,
        @Schema(type = "string", description = "사용 시각 (미사용이면 null)", example = "2024-01-20T13:00:00", nullable = true)
        LocalDateTime usedAt,
        @Schema(type = "string", description = "만료 시각", example = "2024-02-15T09:00:00")
        LocalDateTime expiresAt,
        @Schema(description = "지금 사용 가능한지 (미사용 + 미만료)", example = "true")
        boolean usable
) {

    /** 회원에게 보여줄 쿠폰 정책. (할인 조건만 담고, 발급 현황은 담지 않는다) */
    @Schema(description = "쿠폰 정책 (회원용)")
    public record CouponPolicy(
            @Schema(description = "쿠폰 ID", example = "1")
            Long couponId,
            @Schema(description = "쿠폰명", example = "신규가입 10% 할인")
            String couponName,
            @Schema(description = "할인 방식 (FIXED: 정액, RATE: 정률)", example = "RATE")
            String discountType,
            @Schema(description = "정액이면 할인 금액(원), 정률이면 할인율(%)", example = "10")
            int discountValue,
            @Schema(description = "정률 할인의 최대 할인 금액 (정액이면 null)", example = "5000", nullable = true)
            Integer maxDiscountAmount,
            @Schema(description = "최소 주문 금액", example = "10000")
            int minOrderAmount,
            @Schema(type = "string", description = "발급 종료 시각", example = "2024-12-31T23:59:59")
            LocalDateTime issueEndAt
    ) {

        public static CouponPolicy from(CouponEntity coupon) {
            return new CouponPolicy(
                    coupon.getCouponId(),
                    coupon.getCouponName(),
                    coupon.getDiscountType().name(),
                    coupon.getDiscountValue(),
                    coupon.getMaxDiscountAmount(),
                    coupon.getMinOrderAmount(),
                    coupon.getIssueEndAt()
            );
        }
    }

    public static MemberCouponResponse from(MemberCouponEntity memberCoupon, LocalDateTime now) {
        return new MemberCouponResponse(
                memberCoupon.getMemberCouponId(),
                CouponPolicy.from(memberCoupon.getCoupon()),
                memberCoupon.isUsed(),
                memberCoupon.getUsedAt(),
                memberCoupon.getExpiresAt(),
                memberCoupon.isUsable(now)
        );
    }
}
