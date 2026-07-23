package org.example.groommvp.domain.coupon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.coupon.dto.MemberCouponResponse;
import org.example.groommvp.domain.coupon.service.CouponService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 API 컨트롤러.
 *
 * <p>응답은 팀 공통 포맷({@link CommonResponse}) 으로 감싸서 반환한다.
 *
 * <p><b>인증:</b> 회원은 JWT 로 인증된 {@link AuthMember} 에서 얻는다.
 * 두 경로 모두 {@code SecurityConfig} 에서 인증 필수로 지정되어 있다.
 */
@Tag(name = "Coupon", description = "쿠폰 API")
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "쿠폰 발급", description = "선착순으로 쿠폰을 발급받습니다. 회원당 1장만 발급되며, 수량이 소진되면 실패합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ResponseEntity<CommonResponse<MemberCouponResponse>> issue(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "발급받을 쿠폰 ID", example = "1", required = true)
            @PathVariable Long couponId) {
        MemberCouponResponse response = couponService.issue(authMember.memberId(), couponId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "쿠폰이 발급되었습니다."));
    }

    @Operation(summary = "내 쿠폰 목록 조회", description = "로그인한 회원이 보유한 쿠폰을 최신 발급순으로 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/members/me/coupons")
    public ResponseEntity<CommonResponse<List<MemberCouponResponse>>> getMyCoupons(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthMember authMember) {
        List<MemberCouponResponse> response = couponService.getMyCoupons(authMember.memberId());
        return ResponseEntity.ok(CommonResponse.success(response, "보유 쿠폰 조회 성공"));
    }
}
