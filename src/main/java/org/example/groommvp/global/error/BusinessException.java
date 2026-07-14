package org.example.groommvp.global.error;

import lombok.Getter;

/**
 * 비즈니스 로직 수행 중 발생하는 예외의 최상위 타입.
 *
 * <p>서비스 계층에서 "상품이 없음", "수량이 잘못됨" 같은 상황을 만나면
 * 알맞은 {@link ErrorCode} 와 함께 이 예외를 던진다. 던져진 예외는
 * {@link GlobalExceptionHandler} 가 잡아 공통 응답 포맷으로 변환한다.
 *
 * <pre>{@code throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND); }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황에 맞는 상세 메시지를 함께 전달할 때 사용한다. */
    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
