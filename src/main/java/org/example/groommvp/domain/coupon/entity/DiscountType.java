package org.example.groommvp.domain.coupon.entity;

import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

/**
 * 쿠폰 할인 방식.
 *
 * <p>할인 금액 계산 규칙을 타입마다 직접 갖는다. 계산이 한 곳에 모여 있어야
 * 주문/결제 어느 쪽에서 쓰든 같은 결과가 나온다.
 */
public enum DiscountType {

    /** 정액 할인. {@code discountValue} 원을 깎는다. */
    FIXED {
        @Override
        public long calculateDiscount(long orderAmount, int discountValue, Integer maxDiscountAmount) {
            return Math.min(discountValue, orderAmount);
        }

        @Override
        public void validateDiscountValue(int discountValue) {
            if (discountValue < 1) {
                throw new BusinessException(ErrorCode.INVALID_COUPON_DISCOUNT_VALUE);
            }
        }
    },

    /** 정률 할인. {@code discountValue} % 를 깎되, 상한({@code maxDiscountAmount})이 있으면 그 이하로 제한한다. */
    RATE {
        @Override
        public long calculateDiscount(long orderAmount, int discountValue, Integer maxDiscountAmount) {
            long discount = orderAmount * discountValue / 100;
            if (maxDiscountAmount != null) {
                discount = Math.min(discount, maxDiscountAmount);
            }
            return Math.min(discount, orderAmount);
        }

        @Override
        public void validateDiscountValue(int discountValue) {
            if (discountValue < 1 || discountValue > MAX_RATE_PERCENT) {
                throw new BusinessException(ErrorCode.INVALID_COUPON_DISCOUNT_VALUE);
            }
        }
    };

    /** 정률 할인율 상한 (%). */
    private static final int MAX_RATE_PERCENT = 100;

    /**
     * 할인 금액을 계산한다.
     *
     * <p>어떤 경우에도 주문 금액을 넘지 않는다. (결제 금액이 음수가 되지 않도록)
     *
     * @param orderAmount       할인 적용 대상 주문 금액
     * @param discountValue     정액이면 금액(원), 정률이면 비율(%)
     * @param maxDiscountAmount 정률 할인의 최대 할인 금액 (nullable, 정액에서는 무시)
     * @return 할인 금액 (0 이상, orderAmount 이하)
     */
    public abstract long calculateDiscount(long orderAmount, int discountValue, Integer maxDiscountAmount);

    /**
     * 이 할인 방식에서 유효한 할인 값인지 검증한다.
     *
     * <p>정률에 상한이 없으면 {@code discountValue = 1000} 같은 값이 그대로 통과해,
     * {@code calculateDiscount} 의 "주문 금액을 넘지 않는다" 규칙에 걸려 <b>전액 할인</b>이 된다.
     * 계산 규칙과 마찬가지로 유효 범위도 타입이 직접 갖는다.
     *
     * @param discountValue 정액이면 금액(원), 정률이면 비율(%)
     * @throws BusinessException 값이 유효 범위를 벗어난 경우
     */
    public abstract void validateDiscountValue(int discountValue);
}
