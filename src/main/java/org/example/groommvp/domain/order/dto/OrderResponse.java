package org.example.groommvp.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;

@Schema(description = "주문 조회 응답")
public record OrderResponse(
        @Schema(description = "주문 ID", example = "42")
        Long orderId,
        @Schema(description = "주문 상태 (COMPLETED: 구매 완료, CANCELED: 취소됨)", example = "COMPLETED")
        OrderStatus status,
        @Schema(description = "총 주문 금액", example = "7500000")
        Long totalPrice,
        @Schema(type = "string", description = "주문 취소 시각 (취소되지 않은 주문은 null)", nullable = true, example = "2024-01-15T11:00:00")
        LocalDateTime canceledAt,
        @Schema(type = "string", description = "주문 생성 시각", example = "2024-01-15T10:30:00")
        LocalDateTime createdAt,
        @Schema(description = "주문 상품 목록")
        List<OrderItemResponse> orderItems
) {

    public static OrderResponse from(Order order, List<OrderItem> orderItems) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getCanceledAt(),
                order.getCreatedAt(),
                orderItems.stream()
                        .map(OrderItemResponse::from)
                        .toList()
        );
    }
}
