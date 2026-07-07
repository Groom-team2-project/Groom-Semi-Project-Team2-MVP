package org.example.groommvp.domain.cancel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

import org.example.groommvp.domain.order.entity.OrderStatus;

// 주문 취소 결과 전체
@Schema(description = "주문 취소 응답")
public record OrderCancelResponse (
    @Schema(description = "취소된 주문 ID", example = "42")
    Long orderId,
    @Schema(description = "주문 상태 (COMPLETED: 구매 완료, CANCELED: 취소됨)", example = "CANCELED")
    OrderStatus status,
    @Schema(type = "string", description = "취소 처리 시각 (서버 로컬 시간 기준 ISO-8601 LocalDateTime 형식, 예: 2024-01-15T11:00:00, 취소 성공 응답에서는 항상 존재)", example = "2024-01-15T11:00:00")
    LocalDateTime canceledAt,
    @Schema(description = "재고가 복구된 품목 목록")
    List<RestoredItemResponse> restoredItems
) {}
