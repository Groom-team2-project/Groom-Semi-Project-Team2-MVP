package org.example.groommvp.domain.order.repository;

import org.example.groommvp.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
