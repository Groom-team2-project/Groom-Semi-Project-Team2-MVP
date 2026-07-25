package org.example.groommvp.domain.coupon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.coupon.dto.CouponCreateRequest;
import org.example.groommvp.domain.coupon.dto.CouponResponse;
import org.example.groommvp.domain.coupon.service.CouponService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 어드민 API 컨트롤러.
 *
 * <p>발급 가능한 쿠폰(정책)을 등록한다. 회원용 발급/조회는 {@link CouponController} 에 있다.
 *
 * <p><b>인가:</b> {@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서 ADMIN 권한 필수로
 * 지정되어 있어, 일반 회원은 접근할 수 없다.
 */
@Tag(name = "Coupon Admin", description = "쿠폰 관리 API (어드민)")
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponService couponService;

    @Operation(summary = "쿠폰 등록", description = "발급 가능한 쿠폰을 생성합니다. (ADMIN 전용)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<CommonResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponCreateRequest request) {
        CouponResponse response = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "쿠폰이 등록되었습니다."));
    }
}
