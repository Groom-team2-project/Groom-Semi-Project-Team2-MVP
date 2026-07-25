package org.example.groommvp.domain.point.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.point.dto.PointBalanceResponse;
import org.example.groommvp.domain.point.dto.PointHistoryResponse;
import org.example.groommvp.domain.point.service.PointService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 API 컨트롤러. (조회 전용)
 *
 * <p>적립/사용은 주문·결제 흐름에서 일어나므로 공개 엔드포인트로 두지 않는다.
 * 회원에게는 잔액과 이력 조회만 제공한다.
 *
 * <p><b>인증:</b> JWT 로 인증된 {@link AuthMember} 에서 회원을 얻는다.
 * 두 경로 모두 {@code SecurityConfig} 에서 인증 필수로 지정되어 있다.
 */
@Tag(name = "Point", description = "포인트 API")
@RestController
@RequestMapping("/api/v1/members/me/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @Operation(summary = "내 포인트 잔액 조회", description = "로그인한 회원의 현재 포인트 잔액을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<CommonResponse<PointBalanceResponse>> getBalance(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthMember authMember) {
        PointBalanceResponse response = pointService.getBalance(authMember.memberId());
        return ResponseEntity.ok(CommonResponse.success(response, "포인트 잔액 조회 성공"));
    }

    @Operation(summary = "내 포인트 이력 조회", description = "로그인한 회원의 포인트 적립/사용 이력을 최신순으로 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/histories")
    public ResponseEntity<CommonResponse<List<PointHistoryResponse>>> getHistories(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthMember authMember) {
        List<PointHistoryResponse> response = pointService.getHistories(authMember.memberId());
        return ResponseEntity.ok(CommonResponse.success(response, "포인트 이력 조회 성공"));
    }
}
