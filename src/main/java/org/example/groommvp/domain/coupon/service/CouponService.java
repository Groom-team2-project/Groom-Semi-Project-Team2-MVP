package org.example.groommvp.domain.coupon.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.coupon.dto.CouponCreateRequest;
import org.example.groommvp.domain.coupon.dto.CouponResponse;
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
import org.springframework.dao.PessimisticLockingFailureException;
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
     * 쿠폰(정책)을 생성한다. (어드민)
     *
     * <p>발급 종료가 시작보다 빠르면 발급 자체가 불가능하므로 기간 정합성을 검증한다.
     *
     * @throws BusinessException 발급 시작이 종료보다 늦은 경우
     */
    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        if (!request.issueStartAt().isBefore(request.issueEndAt())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        CouponEntity coupon = couponRepository.save(request.toEntity());
        return CouponResponse.from(coupon);
    }

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
        CouponEntity coupon = findCouponForIssue(couponId);

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

    /**
     * 발급용으로 쿠폰 행에 비관적 락을 걸어 조회한다.
     *
     * <p>인기 쿠폰에 요청이 몰려 락 획득이 타임아웃되면 스레드를 오래 붙잡는 대신
     * {@link ErrorCode#COUPON_ISSUE_BUSY} 로 변환해 클라이언트가 재시도하도록 유도한다.
     * (타임아웃 값은 {@code CouponRepository} 의 {@code @QueryHints} 참고.)
     */
    private CouponEntity findCouponForIssue(Long couponId) {
        try {
            return couponRepository.findByIdWithPessimisticLock(couponId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_BUSY);
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
        MemberCouponEntity memberCoupon = findOwnedCouponWithLock(memberId, memberCouponId);
        return memberCoupon.use(orderAmount, orderId, LocalDateTime.now());
    }

    /**
     * 주문 취소/결제 실패 시 쿠폰 사용을 되돌린다. (파트 C/D 연동용 진입점)
     *
     * <p><b>멱등성:</b> {@code orderId} 를 받아 그 주문에 사용된 경우에만 되돌린다. 주문 A 의
     * 취소가 주문 B 에 쓴 쿠폰을 되살리는 일이 없고, 취소 이벤트가 재전송돼도 안전하다.
     * ({@code PointService#cancelUse} 와 같은 방식)
     *
     * @param orderId 취소 대상 주문 ID (필수)
     */
    @Transactional
    public void cancelCouponUse(Long memberId, Long memberCouponId, Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        MemberCouponEntity memberCoupon = findOwnedCouponWithLock(memberId, memberCouponId);
        memberCoupon.cancelUseFor(orderId);
    }

    /**
     * 보유 쿠폰을 <b>락을 걸어</b> 조회하고 소유권을 검증한다.
     *
     * <p>사용/사용취소는 모두 read-modify-write 라서 락 없이는 동시 요청이 같은 상태를 읽고
     * 각자 갱신한다. 락 획득 후에 소유권을 보므로, 검증과 갱신 사이에 상태가 바뀔 수 없다.
     */
    private MemberCouponEntity findOwnedCouponWithLock(Long memberId, Long memberCouponId) {
        MemberCouponEntity memberCoupon = memberCouponRepository
                .findByIdWithPessimisticLock(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_COUPON_NOT_FOUND));
        if (!memberCoupon.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_COUPON_FORBIDDEN);
        }
        return memberCoupon;
    }
}
