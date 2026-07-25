package org.example.groommvp.domain.product.repository;

import org.example.groommvp.domain.product.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<ImageEntity, Long> {

    long countByProductProductId(Long productId);

    List<ImageEntity> findAllByProductProductIdOrderByImageIdAsc(Long productId);

    Optional<ImageEntity> findByImageIdAndProductProductId(Long imageId, Long productId);
}
