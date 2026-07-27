package org.example.groommvp.domain.coupon.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCouponRepository extends JpaRepository<MemberCouponEntity, Long> {

    boolean existsByMember_MemberIdAndCoupon_CouponId(Long memberId, Long couponId);

    /** 회원이 보유한 쿠폰 목록. 쿠폰 정책까지 함께 조회한다. (N+1 방지) */
    @Query("select mc from MemberCouponEntity mc "
            + "join fetch mc.coupon "
            + "where mc.member.memberId = :memberId "
            + "order by mc.createdAt desc")
    List<MemberCouponEntity> findByMemberIdWithCoupon(@Param("memberId") Long memberId);

    /** 보유 쿠폰 단건 조회. 할인 계산에 쿠폰 정책이 필요하므로 함께 조회한다. */
    @Query("select mc from MemberCouponEntity mc "
            + "join fetch mc.coupon "
            + "where mc.memberCouponId = :memberCouponId")
    Optional<MemberCouponEntity> findByIdWithCoupon(@Param("memberCouponId") Long memberCouponId);

    /**
     * 사용/사용취소용 조회. 보유 쿠폰 행에 비관적 쓰기 락을 걸어 동시 사용을 직렬화한다.
     *
     * <p>사용 처리는 "미사용인지 확인 → 사용으로 표시" 인 read-modify-write 라서, 락 없이는 동시
     * 요청이 둘 다 미사용으로 읽고 각자 다른 주문에 할인을 적용한다. 쿠폰 1장으로 2건이 할인되고
     * DB 에는 사용 1건만 남는다. 발급({@code CouponRepository#findByIdWithPessimisticLock}) 과
     * 같은 패턴으로 막는다.
     *
     * <p>쿠폰 정책({@code coupon})은 fetch join 하지 않는다. 락과 조인을 섞으면 DB 마다 잠기는
     * 행이 달라지므로, 정책은 필요 시점에 지연 로딩으로 가져온다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mc from MemberCouponEntity mc where mc.memberCouponId = :memberCouponId")
    Optional<MemberCouponEntity> findByIdWithPessimisticLock(@Param("memberCouponId") Long memberCouponId);
}
