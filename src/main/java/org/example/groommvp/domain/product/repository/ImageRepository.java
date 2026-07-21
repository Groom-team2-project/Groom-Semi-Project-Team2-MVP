package org.example.groommvp.domain.product.repository;

import org.example.groommvp.domain.product.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageRepository extends JpaRepository<ImageEntity, Long> {

    boolean existsByProductProductId(Long productId);

    Optional<ImageEntity> findByProductProductId(Long productId);
}
