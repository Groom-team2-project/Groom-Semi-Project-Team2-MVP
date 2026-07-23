package org.example.groommvp.domain.review.repository;

import org.example.groommvp.domain.review.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    List<ReviewEntity> findByProductIdAndDeletedAtIsNull(Long productId);

    Optional<ReviewEntity> findByReviewIdAndDeletedAtIsNull(Long reviewId);

    boolean existsByProductIdAndMemberIdAndDeletedAtIsNull(
            Long productId,
            Long memberId
    );
}
