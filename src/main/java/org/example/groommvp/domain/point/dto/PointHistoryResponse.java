package org.example.groommvp.domain.point.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;

/**
 * 포인트 변동 이력 응답 DTO.
 */
@Schema(description = "포인트 변동 이력")
public record PointHistoryResponse(
        @Schema(description = "이력 ID", example = "10")
        Long pointHistoryId,
        @Schema(description = "변동 타입 (EARN: 적립, USE: 사용, CANCEL: 취소복구)", example = "EARN")
        String type,
        @Schema(description = "변동 금액 (양수)", example = "500")
        long amount,
        @Schema(description = "변동 후 잔액", example = "3500")
        long balanceAfter,
        @Schema(description = "관련 주문 ID (없으면 null)", example = "42", nullable = true)
        Long orderId,
        @Schema(type = "string", description = "변동 시각", example = "2024-01-15T10:30:00")
        LocalDateTime createdAt
) {

    public static PointHistoryResponse from(PointHistoryEntity history) {
        return new PointHistoryResponse(
                history.getPointHistoryId(),
                history.getType().name(),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getOrderId(),
                history.getCreatedAt()
        );
    }
}
