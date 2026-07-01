package org.example.groommvp.domain.order.dto;

import java.time.LocalDateTime;

public record PurchaseResponse(
        Long orderId,
        Long productId,
        int purchasedQuantity,
        int remainingStockQuantity,
        LocalDateTime orderedAt
) {
}
