package org.example.groommvp.domain.cart.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByMember_MemberId(Long memberId);

    /**
     * 장바구니 행을 잠가 조회한다.
     *
     * <p><b>주의 — 장바구니가 없을 수 있는 경로에서는 쓰지 말 것.</b> 존재하지 않는 행을
     * {@code FOR UPDATE} 하면 InnoDB 가 레코드 락이 아니라 <b>갭 락</b>을 잡고, 뒤따르는 INSERT 의
     * insert-intention 락과 충돌해 서로 다른 회원의 첫 담기끼리도 교착한다. 그래서
     * {@code CartService} 의 담기 경로는 이 메서드 대신 <b>회원 행</b>을 잠근다.
     *
     * <p>장바구니가 반드시 존재한다고 보장되는 곳에서만 안전하다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartEntity c where c.member.memberId = :memberId")
    Optional<CartEntity> findByMemberIdWithPessimisticLock(@Param("memberId") Long memberId);

    /**
     * 회원의 장바구니를 회원·항목·상품까지 한 번에 조회한다. (N+1 방지)
     *
     * <p>조회 화면/주문 전환처럼 항목 전체를 순회하는 경우에 사용한다.
     * 지연 로딩된 회원 프록시 초기화를 피하려고 {@code member} 도 함께 fetch 한다.
     */
    @Query("select distinct c from CartEntity c "
            + "join fetch c.member "
            + "left join fetch c.items i "
            + "left join fetch i.product "
            + "where c.member.memberId = :memberId")
    Optional<CartEntity> findByMemberIdWithItems(@Param("memberId") Long memberId);
}
