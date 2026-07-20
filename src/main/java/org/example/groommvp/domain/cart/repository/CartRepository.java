package org.example.groommvp.domain.cart.repository;

import java.util.Optional;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByMemberId(Long memberId);

    /**
     * 회원의 장바구니를 항목·상품까지 한 번에 조회한다. (N+1 방지)
     *
     * <p>조회 화면/주문 전환처럼 항목 전체를 순회하는 경우에 사용한다.
     */
    @Query("select distinct c from CartEntity c "
            + "left join fetch c.items i "
            + "left join fetch i.product "
            + "where c.memberId = :memberId")
    Optional<CartEntity> findByMemberIdWithItems(@Param("memberId") Long memberId);
}
