package org.example.groommvp.domain.order.repository;

import java.util.List;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(Order order);
}
