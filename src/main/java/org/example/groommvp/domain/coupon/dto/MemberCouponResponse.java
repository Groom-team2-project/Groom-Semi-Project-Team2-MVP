package org.example.groommvp.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;

/**
 * 회원이 보유한 쿠폰 응답 DTO.
 *
 * <p>보유 정보(사용 여부/만료일)와 쿠폰 정책(할인 조건)을 함께 담는다.
 */
@Schema(description = "보유 쿠폰 정보")
public record MemberCouponResponse(
        @Schema(description = "보유 쿠폰 ID (주문 시 이 ID로 쿠폰을 지정한다)", example = "10")
        Long memberCouponId,
        @Schema(description = "쿠폰 정책")
        CouponResponse coupon,
        @Schema(description = "사용 여부", example = "false")
        boolean used,
        @Schema(type = "string", description = "사용 시각 (미사용이면 null)", example = "2024-01-20T13:00:00", nullable = true)
        LocalDateTime usedAt,
        @Schema(type = "string", description = "만료 시각", example = "2024-02-15T09:00:00")
        LocalDateTime expiresAt,
        @Schema(description = "지금 사용 가능한지 (미사용 + 미만료)", example = "true")
        boolean usable
) {

    public static MemberCouponResponse from(MemberCouponEntity memberCoupon, LocalDateTime now) {
        return new MemberCouponResponse(
                memberCoupon.getMemberCouponId(),
                CouponResponse.from(memberCoupon.getCoupon()),
                memberCoupon.isUsed(),
                memberCoupon.getUsedAt(),
                memberCoupon.getExpiresAt(),
                memberCoupon.isUsable(now)
        );
    }
}
