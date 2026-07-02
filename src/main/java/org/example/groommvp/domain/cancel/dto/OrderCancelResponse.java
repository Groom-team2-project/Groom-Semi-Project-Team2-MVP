package org.example.groommvp.domain.cancel.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.example.groommvp.domain.order.entity.OrderStatus;

// 주문 취소 결과 전체
public record OrderCancelResponse (
    Long orderId,
    OrderStatus status,
    LocalDateTime canceledAt,
    List<RestoredItemResponse> restoredItems
) {}
