package org.example.groommvp.domain.point.repository;

import java.util.List;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntity, Long> {

    /** 회원의 포인트 변동 이력. 최신순. */
    List<PointHistoryEntity> findByMember_MemberIdOrderByCreatedAtDesc(Long memberId);
}
