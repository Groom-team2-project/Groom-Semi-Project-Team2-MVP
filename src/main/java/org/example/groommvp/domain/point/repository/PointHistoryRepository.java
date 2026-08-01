package org.example.groommvp.domain.point.repository;

import java.util.List;
import java.util.Optional;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;
import org.example.groommvp.domain.point.entity.PointHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntity, Long> {

    /** 회원의 포인트 변동 이력. 최신순. */
    List<PointHistoryEntity> findByMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    /** 특정 주문에 대해 해당 타입의 이력이 이미 있는지. (취소 복구 멱등성 판정용) */
    boolean existsByMember_MemberIdAndOrderIdAndType(Long memberId, Long orderId, PointHistoryType type);

    /**
     * 특정 주문의 해당 타입 이력. (원 사용액 확인용)
     *
     * <p>{@code (member_id, order_id, type)} 유니크 제약이 있어 결과는 정상적으로 0 또는 1건이다.
     * 그럼에도 {@code findFirstBy...} 로 결과를 1건으로 제한하는 이유는, 운영 DB 에서 제약이
     * 누락되거나 마이그레이션 중 중복 행이 들어왔을 때 {@code Optional} 반환 쿼리가
     * {@code IncorrectResultSizeDataAccessException} 을 던져 <b>포인트 취소 자체가 500 으로
     * 실패</b>하기 때문이다. 데이터 이상이 취소 흐름을 막는 것보다는, 가장 오래된 이력을 기준으로
     * 복구하고 이상은 별도로 잡는 편이 낫다.
     */
    Optional<PointHistoryEntity> findFirstByMember_MemberIdAndOrderIdAndTypeOrderByPointHistoryIdAsc(
            Long memberId, Long orderId, PointHistoryType type);
}
