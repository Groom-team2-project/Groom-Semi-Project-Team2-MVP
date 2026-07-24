package org.example.groommvp.domain.coupon.repository;

import java.util.List;
import java.util.Optional;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
