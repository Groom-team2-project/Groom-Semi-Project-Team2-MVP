package org.example.groommvp.domain.review.repository;

import org.example.groommvp.domain.review.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    List<ReviewEntity> findByProductId(Long productId);

    boolean existsByProductIdAndMemberIdAndDeletedAtIsNull(
            Long productId,
            Long memberId
    );
}
