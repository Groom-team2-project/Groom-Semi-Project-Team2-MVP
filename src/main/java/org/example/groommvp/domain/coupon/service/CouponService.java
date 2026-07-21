package org.example.groommvp.domain.coupon.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.coupon.dto.MemberCouponResponse;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.example.groommvp.domain.coupon.repository.CouponRepository;
import org.example.groommvp.domain.coupon.repository.MemberCouponRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급/조회/사용 서비스.
 *
 * <p><b>중복 발급 방지 2중 장치:</b> 애플리케이션에서 보유 여부를 먼저 확인하고,
 * 동시 요청으로 그 검사를 통과해버린 경우는 {@code member_coupons} 유니크 제약이 막는다.
 * 제약 위반은 {@link ErrorCode#COUPON_ALREADY_ISSUED} 로 변환한다.
 *
 * <p><b>선착순 수량:</b> 쿠폰 행에 비관적 락을 걸어 발급 수량 증가를 직렬화한다.
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberRepository memberRepository;

    /**
     * 쿠폰을 발급한다. (선착순)
     *
     * @throws BusinessException 쿠폰이 없거나, 발급 기간이 아니거나, 소진됐거나, 이미 발급받은 경우
     */
    @Transactional
    public MemberCouponResponse issue(Long memberId, Long couponId) {
        LocalDateTime now = LocalDateTime.now();

        if (memberCouponRepository.existsByMember_MemberIdAndCoupon_CouponId(memberId, couponId)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }

        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        CouponEntity coupon = couponRepository.findByIdWithPessimisticLock(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        // 발급 기간·잔여 수량 검증을 포함해 수량을 증가시킨다. (락 구간 안에서 수행)
        coupon.issue(now);

        try {
            MemberCouponEntity issued = memberCouponRepository.saveAndFlush(
                    MemberCouponEntity.issue(member, coupon, now));
            return MemberCouponResponse.from(issued, now);
        } catch (DataIntegrityViolationException e) {
            // 위 존재 검사를 동시에 통과한 요청이 있는 경우 — 유니크 제약이 최종 방어선.
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
    }

    /** 내가 보유한 쿠폰 목록. */
    @Transactional(readOnly = true)
    public List<MemberCouponResponse> getMyCoupons(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return memberCouponRepository.findByMemberIdWithCoupon(memberId).stream()
                .map(memberCoupon -> MemberCouponResponse.from(memberCoupon, now))
                .toList();
    }

    /**
     * 보유 쿠폰을 사용 처리하고 할인 금액을 반환한다.
     *
     * <p>주문/결제 흐름(파트 C/D)에서 호출하기 위한 진입점이다. 아직 장바구니 주문에는
     * 연결하지 않았고, 금액 계산 규칙을 이 도메인 안에 모아두기 위해 먼저 제공한다.
     *
     * @param memberId    사용 요청 회원 (소유권 검증용)
     * @param orderAmount 할인 적용 대상 주문 금액
     * @param orderId     사용처 주문 ID
     * @return 할인 금액
     * @throws BusinessException 남의 쿠폰이거나, 이미 사용/만료됐거나, 최소 주문 금액 미달인 경우
     */
    @Transactional
    public long useCoupon(Long memberId, Long memberCouponId, long orderAmount, Long orderId) {
        MemberCouponEntity memberCoupon = memberCouponRepository.findByIdWithCoupon(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_COUPON_NOT_FOUND));
        if (!memberCoupon.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_FORBIDDEN);
        }
        return memberCoupon.use(orderAmount, orderId, LocalDateTime.now());
    }

    /** 주문 취소/결제 실패 시 쿠폰 사용을 되돌린다. (파트 C/D 연동용 진입점) */
    @Transactional
    public void cancelCouponUse(Long memberId, Long memberCouponId) {
        MemberCouponEntity memberCoupon = memberCouponRepository.findByIdWithCoupon(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_COUPON_NOT_FOUND));
        if (!memberCoupon.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_FORBIDDEN);
        }
        memberCoupon.cancelUse();
    }
}
