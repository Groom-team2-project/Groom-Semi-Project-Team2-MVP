package org.example.groommvp.domain.product.repository;

import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
}
