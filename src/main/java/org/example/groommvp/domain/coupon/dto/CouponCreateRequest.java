package org.example.groommvp.domain.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.DiscountType;

/**
 * 쿠폰 생성(어드민) 요청 DTO.
 *
 * <pre>
 * POST /api/v1/admin/coupons
 * </pre>
 *
 * <p>{@code maxDiscountAmount} 는 정률 쿠폰의 최대 할인 금액으로, 정액 쿠폰에서는 무시된다.
 */
@Schema(description = "쿠폰 생성 요청 (어드민)")
public record CouponCreateRequest(
        @Schema(description = "쿠폰명", example = "신규가입 10% 할인", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "쿠폰명은 필수입니다.")
        String couponName,

        @Schema(description = "할인 방식 (FIXED: 정액, RATE: 정률)", example = "RATE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "할인 방식은 필수입니다.")
        DiscountType discountType,

        @Schema(description = "정액이면 할인 금액(원), 정률이면 할인율(%)", example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "할인 값은 필수입니다.")
        @Min(value = 1, message = "할인 값은 1 이상이어야 합니다.")
        Integer discountValue,

        @Schema(description = "정률 할인의 최대 할인 금액 (정액이면 생략)", example = "5000", nullable = true)
        @Min(value = 1, message = "최대 할인 금액은 1 이상이어야 합니다.")
        Integer maxDiscountAmount,

        @Schema(description = "최소 주문 금액", example = "10000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "최소 주문 금액은 필수입니다.")
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다.")
        Integer minOrderAmount,

        @Schema(description = "총 발급 가능 수량", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "발급 수량은 필수입니다.")
        @Min(value = 1, message = "발급 수량은 1 이상이어야 합니다.")
        Integer totalQuantity,

        @Schema(description = "발급 시작 시각", example = "2024-01-01T00:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "발급 시작 시각은 필수입니다.")
        LocalDateTime issueStartAt,

        @Schema(description = "발급 종료 시각", example = "2024-12-31T23:59:59",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "발급 종료 시각은 필수입니다.")
        @Future(message = "발급 종료 시각은 미래여야 합니다.")
        LocalDateTime issueEndAt,

        @Schema(description = "발급일로부터 사용 가능 일수", example = "30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "사용 가능 일수는 필수입니다.")
        @Min(value = 1, message = "사용 가능 일수는 1 이상이어야 합니다.")
        Integer validDays
) {

    public CouponEntity toEntity() {
        return CouponEntity.builder()
                .couponName(couponName)
                .discountType(discountType)
                .discountValue(discountValue)
                .maxDiscountAmount(maxDiscountAmount)
                .minOrderAmount(minOrderAmount)
                .totalQuantity(totalQuantity)
                .issueStartAt(issueStartAt)
                .issueEndAt(issueEndAt)
                .validDays(validDays)
                .build();
    }
}
