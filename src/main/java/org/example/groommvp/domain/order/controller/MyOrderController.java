package org.example.groommvp.domain.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.order.dto.OrderResponse;
import org.example.groommvp.domain.order.service.OrderQueryService;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 주문 내역 API.
 *
 * <p>주문 단건 조회는 {@link OrderController}({@code /api/v1/orders/{orderId}}) 에 있고,
 * 여기는 <b>내 주문 목록</b>만 담당한다. 경로를 {@code /api/v1/members/me/**} 로 둔 이유는
 * 마이페이지 API(장바구니·쿠폰·포인트)와 한 묶음이고, {@code SecurityConfig} 에서 이미
 * 인증 필수로 지정된 경로이기 때문이다.
 *
 * <p><b>조회 범위는 항상 인증된 본인이다.</b> 회원 ID를 파라미터로 받지 않으므로 남의 주문
 * 내역을 요청할 방법 자체가 없다.
 */
@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/api/v1/members/me/orders")
@RequiredArgsConstructor
public class MyOrderController {

    private final OrderQueryService orderQueryService;

    @Operation(summary = "내 주문 내역 조회",
            description = "로그인한 회원의 주문을 최신순으로 조회합니다. 주문이 없으면 빈 배열을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<CommonResponse<List<OrderResponse>>> getMyOrders(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthMember authMember) {
        if (authMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<OrderResponse> response = orderQueryService.getMyOrders(authMember.memberId());
        return ResponseEntity.ok(CommonResponse.success(response, "주문 내역 조회 성공"));
    }
}
