package org.example.groommvp.domain.point.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 포인트 잔액 응답 DTO.
 */
@Schema(description = "포인트 잔액 응답")
public record PointBalanceResponse(
        @Schema(description = "회원 ID", example = "100")
        Long memberId,
        @Schema(description = "현재 포인트 잔액", example = "3500")
        long balance
) {

    public static PointBalanceResponse of(Long memberId, long balance) {
        return new PointBalanceResponse(memberId, balance);
    }
}
