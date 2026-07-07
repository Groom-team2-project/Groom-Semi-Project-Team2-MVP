package org.example.groommvp.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.example.groommvp.domain.cancel.dto.OrderCancelResponse;
import org.example.groommvp.domain.order.dto.PurchaseResponse;
import org.example.groommvp.domain.product.dto.ProductPageResponse;
import org.example.groommvp.domain.product.dto.ProductResponse;
import org.example.groommvp.domain.stock.dto.StockHistoryResponse;
import org.example.groommvp.domain.stock.dto.StockResponse;

/**
 * Swagger 문서 전용 공통 응답 스키마.
 *
 * <p>런타임 응답은 {@link CommonResponse}를 사용하지만, OpenAPI에서 제네릭 data 타입이
 * Object로 뭉개지지 않도록 실제 엔드포인트별 data 타입을 명시합니다.
 */
public final class SwaggerResponse {

    private SwaggerResponse() {
    }

    @Schema(name = "ProductCommonResponse", description = "상품 단건 조회 성공 응답")
    public record ProductCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "응답 데이터")
            ProductResponse data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "null")
            String message
    ) {
    }

    @Schema(name = "ProductPageCommonResponse", description = "상품 목록 조회 성공 응답")
    public record ProductPageCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "페이지 응답 데이터")
            ProductPageResponse data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "상품 목록 조회 성공")
            String message
    ) {
    }

    @Schema(name = "PurchaseCommonResponse", description = "상품 구매 성공 응답")
    public record PurchaseCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "구매 응답 데이터")
            PurchaseResponse data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "구매가 정상적으로 처리되었습니다.")
            String message
    ) {
    }

    @Schema(name = "StockHistoryCommonResponse", description = "재고 변동 단건 성공 응답")
    public record StockHistoryCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "재고 변동 이력 데이터")
            StockHistoryResponse data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "입고가 완료되었습니다.")
            String message
    ) {
    }

    @Schema(name = "StockCommonResponse", description = "현재 재고 조회 성공 응답")
    public record StockCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "현재 재고 데이터")
            StockResponse data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "재고 조회 성공")
            String message
    ) {
    }

    @Schema(name = "StockHistoryListCommonResponse", description = "재고 변동 이력 목록 성공 응답")
    public record StockHistoryListCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "재고 변동 이력 목록")
            List<StockHistoryResponse> data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "재고 변동 이력 조회 성공")
            String message
    ) {
    }

    @Schema(name = "OrderCancelCommonResponse", description = "주문 취소 성공 응답")
    public record OrderCancelCommonResponse(
            @Schema(description = "요청 성공 여부", example = "true")
            boolean success,
            @Schema(description = "주문 취소 응답 데이터")
            OrderCancelResponse data,
            @Schema(description = "성공 응답에서는 null", nullable = true, example = "null")
            String errorCode,
            @Schema(description = "응답 메시지", nullable = true, example = "주문이 취소되었습니다.")
            String message
    ) {
    }
}
