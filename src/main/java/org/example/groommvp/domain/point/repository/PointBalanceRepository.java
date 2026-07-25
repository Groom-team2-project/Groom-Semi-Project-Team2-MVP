package org.example.groommvp.domain.point.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.example.groommvp.domain.point.entity.PointBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointBalanceRepository extends JpaRepository<PointBalanceEntity, Long> {

    Optional<PointBalanceEntity> findByMember_MemberId(Long memberId);

    /**
     * 적립/사용용 조회. 잔액 행에 비관적 쓰기 락을 걸어 동시 변동을 직렬화한다.
     *
     * <p>잔액 증감은 read-modify-write 라서, 락 없이는 동시 요청이 같은 잔액을 읽고 각자
     * 갱신해 잔액이 어긋난다. (재고/쿠폰과 같은 패턴)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PointBalanceEntity b where b.member.memberId = :memberId")
    Optional<PointBalanceEntity> findByMemberIdWithPessimisticLock(@Param("memberId") Long memberId);
}
