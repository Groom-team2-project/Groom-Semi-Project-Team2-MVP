package org.example.groommvp.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
        @Schema(description = "요청 성공 여부", example = "false")
        boolean success,
        @Schema(type = "null", description = "에러 응답에서는 항상 null", nullable = true, example = "null", accessMode = Schema.AccessMode.READ_ONLY)
        Void data,
        @Schema(description = "에러 코드", example = "INVALID_INPUT_VALUE")
        String errorCode,
        @Schema(description = "에러 메시지", example = "입력값이 올바르지 않습니다.")
        String message
) {
}
