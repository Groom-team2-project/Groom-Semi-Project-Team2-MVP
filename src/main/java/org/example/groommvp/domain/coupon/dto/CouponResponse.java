package org.example.groommvp.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.example.groommvp.domain.coupon.entity.CouponEntity;

/**
 * 쿠폰 정책 응답 DTO. (발급 가능한 쿠폰의 조건)
 */
@Schema(description = "쿠폰 정보")
public record CouponResponse(
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
        @Schema(description = "남은 발급 수량", example = "42")
        int remainingQuantity,
        @Schema(type = "string", description = "발급 종료 시각", example = "2024-12-31T23:59:59")
        LocalDateTime issueEndAt
) {

    public static CouponResponse from(CouponEntity coupon) {
        return new CouponResponse(
                coupon.getCouponId(),
                coupon.getCouponName(),
                coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderAmount(),
                coupon.getRemainingQuantity(),
                coupon.getIssueEndAt()
        );
    }
}
