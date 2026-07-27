package org.example.groommvp.domain.order.repository;

import java.util.List;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(Order order);

    @Query("select oi from OrderItem oi join fetch oi.product where oi.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") Long orderId);

    //구매 이력 확인 쿼리
    @Query("""
        select case when count(oi) > 0 then true else false end
        from OrderItem oi
        where oi.order.memberId = :memberId
          and oi.product.productId = :productId
          and oi.order.status = :status
        """)
    boolean existsByMemberIdAndProductIdAndOrderStatus(
            @Param("memberId") Long memberId,
            @Param("productId") Long productId,
            @Param("status") OrderStatus status
    );
}
