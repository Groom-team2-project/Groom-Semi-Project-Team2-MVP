package org.example.groommvp.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 프로젝트 전역에서 사용하는 에러 코드 정의.
 *
 * <p>각 도메인에서 비즈니스 예외가 필요하면 여기에 에러 코드를 추가하고,
 * {@link BusinessException} 과 함께 던지면 {@link GlobalExceptionHandler} 가
 * 공통 응답 포맷으로 변환해 준다. 네이밍 규칙: {@code 도메인_상황}.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 (Common) =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // ===== 상품 (Product) =====
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),

    // ===== 재고/입고 (Stock) =====
    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "입고 수량은 1 이상이어야 합니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
