package org.example.groommvp.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import org.example.groommvp.domain.coupon.dto.MemberCouponResponse;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.DiscountType;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.example.groommvp.domain.coupon.repository.CouponRepository;
import org.example.groommvp.domain.coupon.repository.MemberCouponRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 쿠폰 서비스 단위 테스트.
 *
 * <p>기획서 E 파트 요구 테스트인 "쿠폰 중복 사용 차단"의 서비스 계층 검증.
 * (발급 단계의 중복 방어 + 사용 단계의 소유권 검증)
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long COUPON_ID = 100L;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private MemberCouponRepository memberCouponRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CouponService couponService;

    private static MemberEntity member(Long memberId) {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-" + memberId, "u@example.com", "회원");
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return member;
    }

    private static CouponEntity coupon(int totalQuantity) {
        LocalDateTime now = LocalDateTime.now();
        return CouponEntity.builder()
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(2000)
                .minOrderAmount(0)
                .totalQuantity(totalQuantity)
                .issueStartAt(now.minusDays(1))
                .issueEndAt(now.plusDays(1))
                .validDays(30)
                .build();
    }

    @Nested
    @DisplayName("발급")
    class Issue {

        @Test
        @DisplayName("발급에 성공하면 쿠폰 수량이 증가하고 보유 쿠폰이 반환된다")
        void issue_success() {
            CouponEntity coupon = coupon(10);
            given(memberCouponRepository.existsByMember_MemberIdAndCoupon_CouponId(MEMBER_ID, COUPON_ID))
                    .willReturn(false);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID)));
            given(couponRepository.findByIdWithPessimisticLock(COUPON_ID)).willReturn(Optional.of(coupon));
            given(memberCouponRepository.saveAndFlush(any(MemberCouponEntity.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            MemberCouponResponse response = couponService.issue(MEMBER_ID, COUPON_ID);

            assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
            assertThat(response.used()).isFalse();
            assertThat(response.usable()).isTrue();
        }

        @Test
        @DisplayName("이미 발급받은 쿠폰이면 중복 발급이 차단된다")
        void issue_throwsWhenAlreadyIssued() {
            given(memberCouponRepository.existsByMember_MemberIdAndCoupon_CouponId(MEMBER_ID, COUPON_ID))
                    .willReturn(true);

            assertThatThrownBy(() -> couponService.issue(MEMBER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_ISSUED);

            // 중복이면 수량을 건드리지 않아야 한다.
            verify(couponRepository, never()).findByIdWithPessimisticLock(any());
            verify(memberCouponRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시 요청으로 존재 검사를 통과해도 유니크 제약이 중복 발급을 막는다")
        void issue_convertsUniqueConstraintViolation() {
            given(memberCouponRepository.existsByMember_MemberIdAndCoupon_CouponId(MEMBER_ID, COUPON_ID))
                    .willReturn(false);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID)));
            given(couponRepository.findByIdWithPessimisticLock(COUPON_ID)).willReturn(Optional.of(coupon(10)));
            given(memberCouponRepository.saveAndFlush(any(MemberCouponEntity.class)))
                    .willThrow(new DataIntegrityViolationException("UK_MEMBER_COUPONS_MEMBER_COUPON"));

            assertThatThrownBy(() -> couponService.issue(MEMBER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_ISSUED);
        }

        @Test
        @DisplayName("수량이 소진된 쿠폰은 발급할 수 없다")
        void issue_throwsWhenSoldOut() {
            CouponEntity soldOut = coupon(1);
            soldOut.issue(LocalDateTime.now());
            given(memberCouponRepository.existsByMember_MemberIdAndCoupon_CouponId(MEMBER_ID, COUPON_ID))
                    .willReturn(false);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID)));
            given(couponRepository.findByIdWithPessimisticLock(COUPON_ID)).willReturn(Optional.of(soldOut));

            assertThatThrownBy(() -> couponService.issue(MEMBER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT);

            verify(memberCouponRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰이면 발급에 실패한다")
        void issue_throwsWhenCouponNotFound() {
            given(memberCouponRepository.existsByMember_MemberIdAndCoupon_CouponId(MEMBER_ID, COUPON_ID))
                    .willReturn(false);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID)));
            given(couponRepository.findByIdWithPessimisticLock(COUPON_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.issue(MEMBER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("사용")
    class UseCoupon {

        @Test
        @DisplayName("보유 쿠폰을 사용하면 할인액이 반환된다")
        void useCoupon_returnsDiscount() {
            MemberCouponEntity memberCoupon =
                    MemberCouponEntity.issue(member(MEMBER_ID), coupon(10), LocalDateTime.now());
            given(memberCouponRepository.findByIdWithCoupon(10L)).willReturn(Optional.of(memberCoupon));

            long discount = couponService.useCoupon(MEMBER_ID, 10L, 10_000L, 42L);

            assertThat(discount).isEqualTo(2000L);
            assertThat(memberCoupon.isUsed()).isTrue();
        }

        @Test
        @DisplayName("다른 회원의 쿠폰은 사용할 수 없다")
        void useCoupon_throwsForOtherMembersCoupon() {
            MemberCouponEntity memberCoupon =
                    MemberCouponEntity.issue(member(MEMBER_ID), coupon(10), LocalDateTime.now());
            given(memberCouponRepository.findByIdWithCoupon(10L)).willReturn(Optional.of(memberCoupon));

            assertThatThrownBy(() -> couponService.useCoupon(OTHER_MEMBER_ID, 10L, 10_000L, 42L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_COUPON_FORBIDDEN);

            assertThat(memberCoupon.isUsed()).isFalse();
        }

        @Test
        @DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다 (중복 사용 차단)")
        void useCoupon_throwsWhenAlreadyUsed() {
            MemberCouponEntity memberCoupon =
                    MemberCouponEntity.issue(member(MEMBER_ID), coupon(10), LocalDateTime.now());
            memberCoupon.use(10_000L, 42L, LocalDateTime.now());
            given(memberCouponRepository.findByIdWithCoupon(10L)).willReturn(Optional.of(memberCoupon));

            assertThatThrownBy(() -> couponService.useCoupon(MEMBER_ID, 10L, 10_000L, 43L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_USED);
        }
    }
}
