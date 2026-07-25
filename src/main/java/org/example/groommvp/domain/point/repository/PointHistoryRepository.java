package org.example.groommvp.domain.point.repository;

import java.util.List;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;
import org.example.groommvp.domain.point.entity.PointHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntity, Long> {

    /** 회원의 포인트 변동 이력. 최신순. */
    List<PointHistoryEntity> findByMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    /** 특정 주문에 대해 해당 타입의 이력이 이미 있는지. (취소 복구 멱등성 판정용) */
    boolean existsByMember_MemberIdAndOrderIdAndType(Long memberId, Long orderId, PointHistoryType type);

    /** 특정 주문의 해당 타입 이력들. (원 사용액 합산용) */
    List<PointHistoryEntity> findByMember_MemberIdAndOrderIdAndType(Long memberId, Long orderId, PointHistoryType type);
}
