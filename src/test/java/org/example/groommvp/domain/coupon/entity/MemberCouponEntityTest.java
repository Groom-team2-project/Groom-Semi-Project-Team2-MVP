package org.example.groommvp.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 보유 쿠폰 단위 테스트. (중복 사용 차단 · 만료 · 소유권)
 *
 * <p>기획서 E 파트 요구 테스트인 "쿠폰 중복 사용 차단"을 검증한다.
 */
class MemberCouponEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    private static MemberEntity member(Long memberId) {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-" + memberId, "u@example.com", "회원");
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return member;
    }

    private static CouponEntity coupon() {
        return CouponEntity.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(2000)
                .minOrderAmount(0)
                .totalQuantity(10)
                .issueStartAt(NOW.minusDays(1))
                .issueEndAt(NOW.plusDays(1))
                .validDays(30)
                .build();
    }

    @Test
    @DisplayName("발급하면 쿠폰의 validDays 만큼 뒤가 만료일이 된다")
    void issue_setsExpiresAtFromValidDays() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);

        assertThat(memberCoupon.getExpiresAt()).isEqualTo(NOW.plusDays(30));
        assertThat(memberCoupon.isUsed()).isFalse();
        assertThat(memberCoupon.isUsable(NOW)).isTrue();
    }

    @Test
    @DisplayName("쿠폰을 사용하면 할인액이 반환되고 사용 상태가 된다")
    void use_marksUsedAndReturnsDiscount() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);

        long discount = memberCoupon.use(10_000L, 42L, NOW);

        assertThat(discount).isEqualTo(2000L);
        assertThat(memberCoupon.isUsed()).isTrue();
        assertThat(memberCoupon.getUsedAt()).isEqualTo(NOW);
        assertThat(memberCoupon.getUsedOrderId()).isEqualTo(42L);
        assertThat(memberCoupon.isUsable(NOW)).isFalse();
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다 (중복 사용 차단)")
    void use_throwsWhenAlreadyUsed() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);
        memberCoupon.use(10_000L, 42L, NOW);

        assertThatThrownBy(() -> memberCoupon.use(10_000L, 43L, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    @DisplayName("중복 사용이 차단되면 최초 사용 정보가 유지된다")
    void use_keepsOriginalUsageAfterDuplicateAttempt() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);
        memberCoupon.use(10_000L, 42L, NOW);

        assertThatThrownBy(() -> memberCoupon.use(10_000L, 999L, NOW.plusHours(1)))
                .isInstanceOf(BusinessException.class);

        assertThat(memberCoupon.getUsedOrderId()).isEqualTo(42L);
        assertThat(memberCoupon.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("만료된 쿠폰은 사용할 수 없다")
    void use_throwsWhenExpired() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);
        LocalDateTime afterExpiry = NOW.plusDays(31);

        assertThat(memberCoupon.isExpired(afterExpiry)).isTrue();
        assertThatThrownBy(() -> memberCoupon.use(10_000L, 42L, afterExpiry))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("사용을 취소하면 다시 사용 가능한 상태가 된다")
    void cancelUse_restoresUsableState() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);
        memberCoupon.use(10_000L, 42L, NOW);

        memberCoupon.cancelUse();

        assertThat(memberCoupon.isUsed()).isFalse();
        assertThat(memberCoupon.getUsedOrderId()).isNull();
        assertThat(memberCoupon.isUsable(NOW)).isTrue();
    }

    @Test
    @DisplayName("다른 회원의 쿠폰이면 소유자가 아니다 (회원 격리)")
    void isOwnedBy_distinguishesOwner() {
        MemberCouponEntity memberCoupon = MemberCouponEntity.issue(member(1L), coupon(), NOW);

        assertThat(memberCoupon.isOwnedBy(1L)).isTrue();
        assertThat(memberCoupon.isOwnedBy(2L)).isFalse();
    }
}
