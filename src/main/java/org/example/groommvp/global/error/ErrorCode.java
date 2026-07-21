package org.example.groommvp.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 상품입니다."),
    PRODUCT_STOCK_REMAINING(HttpStatus.CONFLICT, "재고가 남아 있어 상품을 삭제할 수 없습니다."),

    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "재고 수량은 1 이상이어야 합니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "재고를 찾을 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 주문입니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "취소할 수 없는 주문 상태입니다."),

    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
    CART_ITEM_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 회원의 장바구니 항목에 접근할 수 없습니다."),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "장바구니가 비어 있어 주문할 수 없습니다."),
    INVALID_CART_QUANTITY(HttpStatus.BAD_REQUEST, "장바구니 수량은 1 이상이어야 합니다."),

    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_ALREADY_EXISTS(HttpStatus.CONFLICT, "상품 이미지는 하나만 등록할 수 있습니다."),

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "중복된 카테고리명입니다."),
    CATEGORY_ALREADY_CHILDREN(HttpStatus.CONFLICT, "중분류 카테고리를 입력하세요."),

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 결제된 주문입니다."),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.CONFLICT, "환불할 수 없는 결제 상태입니다."),
    PAYMENT_NOT_PENDING(HttpStatus.CONFLICT, "결제 대기 상태의 주문이 아닙니다.");

    private final HttpStatus status;
    private final String message;
}
