package org.example.groommvp.domain.order.repository;

import java.util.Optional;

import org.example.groommvp.domain.order.entity.Order;
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
}
