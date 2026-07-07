package org.example.groommvp.domain.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.stock.dto.StockHistoryResponse;
import org.example.groommvp.domain.stock.dto.StockInRequest;
import org.example.groommvp.domain.stock.dto.StockResponse;
import org.example.groommvp.domain.stock.service.StockService;
import org.example.groommvp.global.response.CommonResponse;
import org.example.groommvp.global.response.ErrorResponse;
import org.example.groommvp.global.response.SwaggerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고/입고 API 컨트롤러.
 *
 * <p>응답은 팀 공통 포맷({@link CommonResponse}) 으로 감싸서 반환한다.
 */
@Tag(name = "Stock", description = "재고 관리 API")
@RestController
@RequestMapping("/api/v1/products/{productId}")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** 상품 입고. */
    @Operation(summary = "상품 입고", description = "상품에 재고를 추가합니다. 입고 완료 후 재고 변동 이력이 INBOUND 타입으로 기록됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "입고 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.StockHistoryCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": {
                                            "historyId": 10,
                                            "stockId": 1,
                                            "productId": 1,
                                            "productName": "MacBook Pro",
                                            "orderId": null,
                                            "type": "INBOUND",
                                            "changedQty": 30,
                                            "currentStocks": 80,
                                            "reason": "정기 입고",
                                            "createdAt": "2024-01-15T09:00:00"
                                        },
                                        "errorCode": null,
                                        "message": "입고가 완료되었습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력값 유효성 검사 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Validation 실패", value = """
                                            {
                                                "success": false,
                                                "data": null,
                                                "errorCode": "INVALID_INPUT_VALUE",
                                                "message": "입력값이 올바르지 않습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "입고 수량 오류", value = """
                                            {
                                                "success": false,
                                                "data": null,
                                                "errorCode": "INVALID_STOCK_QUANTITY",
                                                "message": "재고 수량은 1 이상이어야 합니다."
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "PRODUCT_NOT_FOUND",
                                        "message": "상품을 찾을 수 없습니다."
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
    @PostMapping("/stock-in")
    public ResponseEntity<CommonResponse<StockHistoryResponse>> stockIn(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId,
            @Valid @RequestBody StockInRequest request) {
        StockHistoryResponse response = stockService.stockIn(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "입고가 완료되었습니다."));
    }

    /** 상품의 현재 재고 조회. */
    @Operation(summary = "현재 재고 조회", description = "상품 ID로 현재 재고 수량과 재고 수정 시각을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.StockCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": {
                                            "productId": 1,
                                            "productName": "MacBook Pro",
                                            "stocks": 50,
                                            "updatedAt": "2024-01-15T09:00:00"
                                        },
                                        "errorCode": null,
                                        "message": "재고 조회 성공"
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
    @GetMapping("/stock")
    public ResponseEntity<CommonResponse<StockResponse>> getStock(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId) {
        StockResponse response = stockService.getStock(productId);
        return ResponseEntity.ok(CommonResponse.success(response, "재고 조회 성공"));
    }

    /** 상품의 재고 변동 이력 조회. */
    @Operation(summary = "재고 변동 이력 조회", description = "특정 상품의 재고 변동 이력 전체를 조회합니다. (INBOUND: 입고, DECREASE: 구매 차감, RESTORE: 취소 복구)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.StockHistoryListCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": [
                                            {
                                                "historyId": 1,
                                                "stockId": 1,
                                                "productId": 1,
                                                "productName": "MacBook Pro",
                                                "orderId": null,
                                                "type": "INBOUND",
                                                "changedQty": 100,
                                                "currentStocks": 100,
                                                "reason": "초기 입고",
                                                "createdAt": "2024-01-10T08:00:00"
                                            },
                                            {
                                                "historyId": 2,
                                                "stockId": 1,
                                                "productId": 1,
                                                "productName": "MacBook Pro",
                                                "orderId": 42,
                                                "type": "DECREASE",
                                                "changedQty": 3,
                                                "currentStocks": 97,
                                                "reason": null,
                                                "createdAt": "2024-01-15T10:30:00"
                                            }
                                        ],
                                        "errorCode": null,
                                        "message": "재고 변동 이력 조회 성공"
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "PRODUCT_NOT_FOUND",
                                        "message": "상품을 찾을 수 없습니다."
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
    @GetMapping("/stock-histories")
    public ResponseEntity<CommonResponse<List<StockHistoryResponse>>> getHistories(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId) {
        List<StockHistoryResponse> response = stockService.getHistories(productId);
        return ResponseEntity.ok(CommonResponse.success(response, "재고 변동 이력 조회 성공"));
    }
}
