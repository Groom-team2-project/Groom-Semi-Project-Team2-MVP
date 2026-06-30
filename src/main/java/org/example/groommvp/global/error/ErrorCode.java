package org.example.groommvp.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),

    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "재고 수량은 1 이상이어야 합니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "재고를 찾을 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다.");

    private final HttpStatus status;
    private final String message;
}
