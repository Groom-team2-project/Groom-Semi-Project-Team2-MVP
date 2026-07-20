package org.example.groommvp.domain.cart.repository;

import org.example.groommvp.domain.cart.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
}
