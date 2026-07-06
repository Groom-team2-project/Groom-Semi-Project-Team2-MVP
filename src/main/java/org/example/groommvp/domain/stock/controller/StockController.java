package org.example.groommvp.domain.stock.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.stock.dto.StockHistoryResponse;
import org.example.groommvp.domain.stock.dto.StockInRequest;
import org.example.groommvp.domain.stock.dto.StockResponse;
import org.example.groommvp.domain.stock.service.StockService;
import org.example.groommvp.global.response.CommonResponse;
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
@RestController
@RequestMapping("/api/v1/products/{productId}")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** 상품 입고. */
    @PostMapping("/stock-in")
    public ResponseEntity<CommonResponse<StockHistoryResponse>> stockIn(
            @PathVariable Long productId,
            @Valid @RequestBody StockInRequest request) {
        StockHistoryResponse response = stockService.stockIn(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "입고가 완료되었습니다."));
    }

    /** 상품의 현재 재고 조회. */
    @GetMapping("/stock")
    public ResponseEntity<CommonResponse<StockResponse>> getStock(
            @PathVariable Long productId) {
        StockResponse response = stockService.getStock(productId);
        return ResponseEntity.ok(CommonResponse.success(response, "재고 조회 성공"));
    }

    /** 상품의 재고 변동 이력 조회. */
    @GetMapping("/stock-histories")
    public ResponseEntity<CommonResponse<List<StockHistoryResponse>>> getHistories(
            @PathVariable Long productId) {
        List<StockHistoryResponse> response = stockService.getHistories(productId);
        return ResponseEntity.ok(CommonResponse.success(response, "재고 변동 이력 조회 성공"));
    }
}
