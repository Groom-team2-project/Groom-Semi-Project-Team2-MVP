package org.example.groommvp.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.DiscountType;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.example.groommvp.domain.coupon.repository.CouponRepository;
import org.example.groommvp.domain.coupon.repository.MemberCouponRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.point.entity.PointBalanceEntity;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;
import org.example.groommvp.domain.point.entity.PointHistoryType;
import org.example.groommvp.domain.point.repository.PointBalanceRepository;
import org.example.groommvp.domain.point.repository.PointHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 유니크 제약이 <b>실제로 DB에 걸려 있는지</b> 검증한다.
 *
 * <p>E 파트의 여러 방어 로직(중복 발급 차단, 포인트 멱등성, 회원당 장바구니 1개)은 애플리케이션
 * 검증을 동시 요청이 통과했을 때 <b>유니크 제약을 최종 방어선</b>으로 삼는다. 그런데 이 프로젝트는
 * {@code ddl-auto: update} 로 스키마를 만들기 때문에, 이미 데이터가 있는 테이블에는 제약이 조용히
 * 추가되지 않을 수 있다. 최종 방어선이 사라져도 아무 경고가 없는 것이 위험하다.
 *
 * <p>그래서 스키마를 들여다보는 대신 <b>실제로 중복을 넣어본다.</b> DB 종류(H2/MySQL)와 무관하게
 * 동작하고, 제약이 빠지면 즉시 실패한다.
 *
 * <p>배포 대상 DB에서 직접 확인하는 SQL 은 {@code docs/pre-deploy-checklist.md} 참고.
 */
@SpringBootTest
class UniqueConstraintTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    private MemberEntity member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(
                MemberEntity.createKakaoMember("uk-test", "uk@example.com", "제약회원"));
    }

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointBalanceRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private CouponEntity newCoupon() {
        LocalDateTime now = LocalDateTime.now();
        return couponRepository.saveAndFlush(CouponEntity.builder()
                .couponName("제약 테스트 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(1000)
                .minOrderAmount(0)
                .totalQuantity(10)
                .issueStartAt(now.minusDays(1))
                .issueEndAt(now.plusDays(1))
                .validDays(30)
                .build());
    }

    @Test
    @DisplayName("carts.member_id — 회원당 장바구니는 하나만 만들 수 있다")
    void carts_memberIdIsUnique() {
        cartRepository.saveAndFlush(CartEntity.init(member));

        assertThatThrownBy(() -> cartRepository.saveAndFlush(CartEntity.init(member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("member_coupons(member_id, coupon_id) — 같은 쿠폰을 두 번 발급받을 수 없다")
    void memberCoupons_memberAndCouponAreUnique() {
        CouponEntity coupon = newCoupon();
        LocalDateTime now = LocalDateTime.now();
        memberCouponRepository.saveAndFlush(MemberCouponEntity.issue(member, coupon, now));

        assertThatThrownBy(() -> memberCouponRepository.saveAndFlush(
                MemberCouponEntity.issue(member, coupon, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("point_balances.member_id — 회원당 잔액 행은 하나만 만들 수 있다")
    void pointBalances_memberIdIsUnique() {
        pointBalanceRepository.saveAndFlush(PointBalanceEntity.init(member));

        assertThatThrownBy(() -> pointBalanceRepository.saveAndFlush(PointBalanceEntity.init(member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("point_histories(member_id, order_id, type) — 같은 주문의 같은 타입 변동은 한 번만 적재된다")
    void pointHistories_memberOrderTypeAreUnique() {
        pointHistoryRepository.saveAndFlush(
                PointHistoryEntity.of(member, PointHistoryType.USE, 100, 0, 42L));

        assertThatThrownBy(() -> pointHistoryRepository.saveAndFlush(
                PointHistoryEntity.of(member, PointHistoryType.USE, 100, 0, 42L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("주문 ID가 없는 포인트 변동은 여러 번 적재될 수 있다 (제약 대상 아님)")
    void pointHistories_nullOrderIdIsNotConstrained() {
        pointHistoryRepository.saveAndFlush(
                PointHistoryEntity.of(member, PointHistoryType.EARN, 100, 100, null));

        // NULL 은 서로 다른 값으로 취급되므로 유니크 제약에 걸리지 않는다.
        // (주문과 무관한 적립을 여러 번 할 수 있어야 한다)
        assertThatCode(() -> pointHistoryRepository.saveAndFlush(
                PointHistoryEntity.of(member, PointHistoryType.EARN, 100, 200, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("타입이 다르면 같은 주문에도 적재된다 (USE 와 CANCEL 공존)")
    void pointHistories_differentTypesCoexistForSameOrder() {
        pointHistoryRepository.saveAndFlush(
                PointHistoryEntity.of(member, PointHistoryType.USE, 100, 0, 42L));

        assertThatCode(() -> pointHistoryRepository.saveAndFlush(
                PointHistoryEntity.of(member, PointHistoryType.CANCEL, 100, 100, 42L)))
                .doesNotThrowAnyException();
    }
}
