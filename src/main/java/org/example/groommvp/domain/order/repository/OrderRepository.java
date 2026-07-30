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

	/**
	 * 회원의 주문 목록. 최신순. (마이페이지 주문 내역)
	 *
	 * <p>{@code createdAt} 은 같은 트랜잭션/같은 밀리초에 만들어진 주문끼리 동률이 될 수 있어
	 * 그것만으로는 정렬이 비결정적이다. 조회할 때마다 순서가 뒤바뀌면 화면도 테스트도 흔들리므로
	 * ID 를 보조 키로 두어 동률을 항상 같은 순서로 끊는다.
	 */
	List<Order> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

	/**
	 * 결제되지 않은 채 방치된 주문의 ID를 오래된 순으로 조회한다. (예약 재고 회수용)
	 *
	 * <p>예약 재고를 되돌릴 대상만 추리는 용도라 엔티티가 아닌 ID만 읽는다. 실제 회수는 주문마다
	 * 별도 트랜잭션에서 행을 잠그고 상태를 다시 확인한 뒤 수행한다.
	 *
	 * <p><b>주문에 적힌 마감 시각을 기준으로 삼는다.</b> 회수 시점을 스케줄러 설정에서 따로
	 * 계산하면 주문의 {@code paymentExpiresAt} 과 어긋난다 — 화면에는 "20분 남음"이라고 띄워두고
	 * 뒤에서는 이미 재고를 회수해 버리는 상황이 생긴다. 사용자에게 약속한 시각이 곧 회수 시각이다.
	 *
	 * <p>{@code paymentExpiresAt} 이 없는 옛 주문은 이 필드가 생기기 전에 만들어진 것들이라
	 * 예전 방식대로 생성 시각으로 판정한다.
	 *
	 * @param now              현재 시각. 마감이 이 시각을 지난 주문이 대상
	 * @param legacyThreshold  마감 시각이 없는 옛 주문용 — 이 시각 이전에 생성됐으면 대상
	 * @param pageable         한 번에 처리할 최대 건수 (밀린 물량이 한 트랜잭션을 오래 잡지 않도록 제한)
	 */
	@Query("select o.id from Order o "
			+ "where o.status = :status "
			+ "and ((o.paymentExpiresAt is not null and o.paymentExpiresAt <= :now) "
			+ "  or (o.paymentExpiresAt is null and o.createdAt < :legacyThreshold)) "
			+ "order by o.createdAt asc")
	List<Long> findIdsByStatusExpiredBefore(@Param("status") OrderStatus status,
			@Param("now") LocalDateTime now,
			@Param("legacyThreshold") LocalDateTime legacyThreshold,
			Pageable pageable);
}
