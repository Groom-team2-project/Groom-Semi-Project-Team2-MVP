package org.example.groommvp.domain.order.controller;

import org.example.groommvp.domain.order.dto.PurchaseRequest;
import org.example.groommvp.domain.order.dto.PurchaseResponse;
import org.example.groommvp.domain.order.service.PurchaseService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/{productId}/orders")
    public ResponseEntity<CommonResponse<PurchaseResponse>> purchase(
            @PathVariable Long productId,
            @Valid @RequestBody PurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(purchaseService.purchase(productId, request), "구매가 정상적으로 처리되었습니다."));
    }
}
