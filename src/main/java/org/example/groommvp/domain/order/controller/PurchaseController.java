package org.example.groommvp.domain.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.groommvp.domain.order.dto.PurchaseRequest;
import org.example.groommvp.domain.order.dto.PurchaseResponse;
import org.example.groommvp.domain.order.service.PurchaseService;
import org.example.groommvp.global.response.CommonResponse;
import org.example.groommvp.global.response.ErrorResponse;
import org.example.groommvp.global.response.SwaggerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Tag(name = "Order", description = "주문 API")
@RestController
@RequestMapping("/api/v1/products")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @Operation(summary = "상품 구매", description = "특정 상품을 지정한 수량만큼 구매합니다. 구매 성공 시 재고가 차감되고 주문이 생성됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "구매 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.PurchaseCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": {
                                            "orderId": 42,
                                            "productId": 1,
                                            "purchasedQuantity": 3,
                                            "remainingStockQuantity": 47,
                                            "orderedAt": "2024-01-15T10:30:00"
                                        },
                                        "errorCode": null,
                                        "message": "구매가 정상적으로 처리되었습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 유효성 검사 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INVALID_INPUT_VALUE",
                                        "message": "입력값이 올바르지 않습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "상품 또는 재고 정보를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "상품 없음", value = """
                                            {
                                                "success": false,
                                                "data": null,
                                                "errorCode": "PRODUCT_NOT_FOUND",
                                                "message": "상품을 찾을 수 없습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "재고 정보 없음", value = """
                                            {
                                                "success": false,
                                                "data": null,
                                                "errorCode": "STOCK_NOT_FOUND",
                                                "message": "재고를 찾을 수 없습니다."
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "409", description = "재고 부족",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "OUT_OF_STOCK",
                                        "message": "재고가 부족합니다."
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
    @PostMapping("/{productId}/orders")
    public ResponseEntity<CommonResponse<PurchaseResponse>> purchase(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId,
            @Valid @RequestBody PurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(purchaseService.purchase(productId, request), "구매가 정상적으로 처리되었습니다."));
    }
}
