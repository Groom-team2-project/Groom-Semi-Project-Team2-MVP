package org.example.groommvp.domain.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.order.dto.OrderResponse;
import org.example.groommvp.domain.order.service.OrderQueryService;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.global.response.CommonResponse;
import org.example.groommvp.global.response.ErrorResponse;
import org.example.groommvp.global.response.SwaggerResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService orderQueryService;

    @Operation(
            summary = "주문 단건 조회",
            description = "인증된 회원 본인의 주문 상태, 총 주문 금액, 주문 상품 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.OrderCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": {
                                            "orderId": 42,
                                            "status": "COMPLETED",
                                            "totalPrice": 7500000,
                                            "canceledAt": null,
                                            "createdAt": "2024-01-15T10:30:00",
                                            "orderItems": [
                                                {
                                                    "orderItemId": 10,
                                                    "productId": 1,
                                                    "productName": "MacBook Pro",
                                                    "quantity": 3,
                                                    "orderPrice": 2500000,
                                                    "itemTotalPrice": 7500000
                                                }
                                            ]
                                        },
                                        "errorCode": null,
                                        "message": "주문 조회 성공"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "UNAUTHORIZED",
                                        "message": "인증이 필요합니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "다른 회원의 주문 접근 불가",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "ORDER_FORBIDDEN",
                                        "message": "다른 회원의 주문에 접근할 수 없습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "ORDER_NOT_FOUND",
                                        "message": "주문을 찾을 수 없습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다."
                                    }
                                    """)))
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<CommonResponse<OrderResponse>> getOrder(
            @Parameter(description = "주문 ID", example = "42", required = true)
            @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthMember authMember
    ) {
        if (authMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        OrderResponse response = orderQueryService.getOrder(orderId, authMember.memberId());
        return ResponseEntity.ok(CommonResponse.success(response, "주문 조회 성공"));
    }
}
