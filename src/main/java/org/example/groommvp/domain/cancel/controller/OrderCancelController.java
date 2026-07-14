package org.example.groommvp.domain.cancel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.groommvp.domain.cancel.dto.OrderCancelResponse;
import org.example.groommvp.domain.cancel.service.OrderCancelService;
import org.example.groommvp.global.response.CommonResponse;
import org.example.groommvp.global.response.ErrorResponse;
import org.example.groommvp.global.response.SwaggerResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCancelController {
    
    private final OrderCancelService orderCancelService;

    public OrderCancelController(OrderCancelService orderCancelService) {
        this.orderCancelService = orderCancelService;
    }

	@Operation(summary = "주문 취소", description = "주문을 취소합니다. 취소 시 차감된 재고가 자동으로 복구됩니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소 성공",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = SwaggerResponse.OrderCancelCommonResponse.class),
							examples = @ExampleObject(value = """
									{
									    "success": true,
									    "data": {
									        "orderId": 42,
									        "status": "CANCELED",
									        "canceledAt": "2024-01-15T11:00:00",
									        "restoredItems": [{"productId": 1, "quantity": 3}]
									    },
									    "errorCode": null,
									    "message": "주문이 취소되었습니다."
									}
									"""))),
			@ApiResponse(responseCode = "404", description = "주문 또는 재고 정보를 찾을 수 없음",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class),
							examples = {
									@ExampleObject(name = "주문 없음", value = """
											{
											    "success": false,
											    "data": null,
											    "errorCode": "ORDER_NOT_FOUND",
											    "message": "주문을 찾을 수 없습니다."
											}
											"""),
									@ExampleObject(name = "재고 정보 없음 (복구 중)", value = """
											{
											    "success": false,
											    "data": null,
											    "errorCode": "STOCK_NOT_FOUND",
											    "message": "재고를 찾을 수 없습니다."
											}
											""")
							})),
			@ApiResponse(responseCode = "409", description = "이미 취소되었거나 취소 불가 상태",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class),
							examples = {
									@ExampleObject(name = "이미 취소됨", value = """
											{
											    "success": false,
											    "data": null,
											    "errorCode": "ORDER_ALREADY_CANCELED",
											    "message": "이미 취소된 주문입니다."
											}
											"""),
									@ExampleObject(name = "취소 불가", value = """
											{
											    "success": false,
											    "data": null,
											    "errorCode": "ORDER_NOT_CANCELABLE",
											    "message": "취소할 수 없는 주문 상태입니다."
											}
											""")
							})),
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
	@PostMapping("/{orderId}/cancel")
	public ResponseEntity<CommonResponse<OrderCancelResponse>> cancel(
		@Parameter(description = "주문 ID", example = "42", required = true)
		@PathVariable Long orderId
	) {
		OrderCancelResponse response = orderCancelService.cancel(orderId);
		return ResponseEntity.ok(
			CommonResponse.success(response, "주문이 취소되었습니다.")
		);
	}
}
