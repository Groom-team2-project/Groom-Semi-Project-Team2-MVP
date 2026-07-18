package org.example.groommvp.domain.payment.repository;

import java.util.Optional;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	// 주문으로 결제 조회
	Optional<Payment> findByOrder(Order order);

	// 이 주문에 이미 결제가 있는지 확인 (이중 결제 방지)
	boolean existsByOrder(Order order);
}
