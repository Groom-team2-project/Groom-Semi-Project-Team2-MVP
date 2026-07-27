package org.example.groommvp.domain.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

	// 주문 조회 시 비관적 쓰기 Lock - 동시 취소 방어
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :orderId")
	Optional<Order> findByIdWithPessimisticLock(@Param("orderId") Long orderId);

	/** 회원의 주문 목록. 최신순. (마이페이지 주문 내역) */
	List<Order> findByMemberIdOrderByCreatedAtDesc(Long memberId);

	/**
	 * 결제되지 않은 채 방치된 주문의 ID를 오래된 순으로 조회한다. (예약 재고 회수용)
	 *
	 * <p>예약 재고를 되돌릴 대상만 추리는 용도라 엔티티가 아닌 ID만 읽는다. 실제 회수는 주문마다
	 * 별도 트랜잭션에서 행을 잠그고 상태를 다시 확인한 뒤 수행한다.
	 *
	 * @param threshold 이 시각 이전에 생성된 주문이 대상
	 * @param pageable  한 번에 처리할 최대 건수 (밀린 물량이 한 트랜잭션을 오래 잡지 않도록 제한)
	 */
	@Query("select o.id from Order o "
			+ "where o.status = :status and o.createdAt < :threshold "
			+ "order by o.createdAt asc")
	List<Long> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status,
			@Param("threshold") LocalDateTime threshold,
			Pageable pageable);
}
