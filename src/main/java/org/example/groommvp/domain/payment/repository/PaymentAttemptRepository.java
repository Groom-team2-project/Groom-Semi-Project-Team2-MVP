package org.example.groommvp.domain.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.groommvp.domain.payment.entity.PaymentAttempt;
import org.example.groommvp.domain.payment.entity.PaymentAttemptStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

	Optional<PaymentAttempt> findByTossOrderId(String tossOrderId);

	/**
	 * 결과가 반영되지 않은 채 방치된 시도를 오래된 순으로 찾는다. (정산 대상)
	 *
	 * @param status    보통 {@link PaymentAttemptStatus#STARTED}
	 * @param threshold 이 시각 이전에 시작된 것이 대상
	 */
	List<PaymentAttempt> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
		PaymentAttemptStatus status, LocalDateTime threshold, Pageable pageable);
}
