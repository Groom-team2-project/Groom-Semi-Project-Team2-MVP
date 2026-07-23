package org.example.groommvp.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 쿠폰 정책 엔티티 단위 테스트. (할인 계산 · 발급 수량/기간)
 */
class CouponEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    private static CouponEntity.CouponEntityBuilder baseCoupon() {
        return CouponEntity.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(1000)
                .minOrderAmount(0)
                .totalQuantity(10)
                .issueStartAt(NOW.minusDays(1))
                .issueEndAt(NOW.plusDays(1))
                .validDays(30);
    }

    @Nested
    @DisplayName("할인 계산")
    class CalculateDiscount {

        @Test
        @DisplayName("정액 쿠폰은 지정한 금액만큼 할인한다")
        void fixed_discountsFixedAmount() {
            CouponEntity coupon = baseCoupon()
                    .discountType(DiscountType.FIXED)
                    .discountValue(3000)
                    .build();

            assertThat(coupon.calculateDiscount(10000L)).isEqualTo(3000L);
        }

        @Test
        @DisplayName("정률 쿠폰은 주문 금액의 비율만큼 할인한다")
        void rate_discountsByPercentage() {
            CouponEntity coupon = baseCoupon()
                    .discountType(DiscountType.RATE)
                    .discountValue(10)
                    .build();

            assertThat(coupon.calculateDiscount(10000L)).isEqualTo(1000L);
        }

        @Test
        @DisplayName("정률 쿠폰의 할인액은 최대 할인 금액을 넘지 않는다")
        void rate_cappedByMaxDiscountAmount() {
            CouponEntity coupon = baseCoupon()
                    .discountType(DiscountType.RATE)
                    .discountValue(50)
                    .maxDiscountAmount(5000)
                    .build();

            // 100,000 의 50% = 50,000 이지만 상한 5,000 으로 제한된다.
            assertThat(coupon.calculateDiscount(100_000L)).isEqualTo(5000L);
        }

        @Test
        @DisplayName("할인액은 주문 금액을 초과하지 않는다 (결제 금액이 음수가 되지 않도록)")
        void discountNeverExceedsOrderAmount() {
            CouponEntity coupon = baseCoupon()
                    .discountType(DiscountType.FIXED)
                    .discountValue(50_000)
                    .build();

            assertThat(coupon.calculateDiscount(3000L)).isEqualTo(3000L);
        }

        @Test
        @DisplayName("최소 주문 금액에 미달하면 예외가 발생한다")
        void throwsWhenBelowMinOrderAmount() {
            CouponEntity coupon = baseCoupon()
                    .minOrderAmount(10_000)
                    .build();

            assertThatThrownBy(() -> coupon.calculateDiscount(9_999L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_MIN_ORDER_NOT_MET);
        }

        @Test
        @DisplayName("최소 주문 금액과 같으면 할인된다 (경계값)")
        void allowsWhenExactlyMinOrderAmount() {
            CouponEntity coupon = baseCoupon()
                    .minOrderAmount(10_000)
                    .discountValue(1000)
                    .build();

            assertThat(coupon.calculateDiscount(10_000L)).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("발급")
    class Issue {

        @Test
        @DisplayName("발급하면 발급 수량이 증가하고 잔여 수량이 감소한다")
        void issue_increasesIssuedQuantity() {
            CouponEntity coupon = baseCoupon().totalQuantity(3).build();

            coupon.issue(NOW);

            assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
            assertThat(coupon.getRemainingQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("수량이 모두 소진되면 발급에 실패한다")
        void issue_throwsWhenSoldOut() {
            CouponEntity coupon = baseCoupon().totalQuantity(1).build();
            coupon.issue(NOW);

            assertThatThrownBy(() -> coupon.issue(NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT);
        }

        @Test
        @DisplayName("발급 시작 전에는 발급에 실패한다")
        void issue_throwsBeforeIssuePeriod() {
            CouponEntity coupon = baseCoupon()
                    .issueStartAt(NOW.plusDays(1))
                    .issueEndAt(NOW.plusDays(2))
                    .build();

            assertThatThrownBy(() -> coupon.issue(NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_ISSUABLE);
        }

        @Test
        @DisplayName("발급 종료 후에는 발급에 실패한다")
        void issue_throwsAfterIssuePeriod() {
            CouponEntity coupon = baseCoupon()
                    .issueStartAt(NOW.minusDays(2))
                    .issueEndAt(NOW.minusDays(1))
                    .build();

            assertThatThrownBy(() -> coupon.issue(NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_ISSUABLE);
        }
    }
}
