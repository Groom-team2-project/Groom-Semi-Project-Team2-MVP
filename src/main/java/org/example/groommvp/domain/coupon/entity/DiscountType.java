package org.example.groommvp.domain.coupon.entity;

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
    };

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
}
