package org.example.groommvp.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.groommvp.domain.order.entity.OrderItem;

@Schema(description = "주문 상품 응답")
public record OrderItemResponse(
        @Schema(description = "주문 상품 ID", example = "10")
        Long orderItemId,
        @Schema(description = "상품 ID", example = "1")
        Long productId,
        @Schema(description = "상품명", example = "MacBook Pro")
        String productName,
        @Schema(description = "주문 수량", example = "3")
        int quantity,
        @Schema(description = "주문 당시 상품 가격", example = "2500000")
        int orderPrice,
        @Schema(description = "주문 상품 총액", example = "7500000")
        int itemTotalPrice
) {

    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProduct().getProductId(),
                orderItem.getProduct().getProductName(),
                orderItem.getQuantity(),
                orderItem.getOrderPrice(),
                orderItem.getQuantity() * orderItem.getOrderPrice()
        );
    }
}
