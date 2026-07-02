package org.example.groommvp.domain.cancel.dto;

// 복구된 품목 (어떤 상품을 몇개 복구 했나)
public record RestoredItemResponse (
    Long productId,
    int quantity
) {}
