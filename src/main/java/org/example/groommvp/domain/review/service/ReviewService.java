package org.example.groommvp.domain.review.service;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.review.dto.ReviewRequest;
import org.example.groommvp.domain.review.dto.ReviewResponse;
import org.example.groommvp.domain.review.dto.ReviewUpdateRequest;
import org.example.groommvp.domain.review.entity.ReviewEntity;
import org.example.groommvp.domain.review.repository.ReviewRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;

    @Transactional
    public ReviewResponse createReview(ReviewRequest reviewRequest, Long loginMemberId) {
        boolean alreadyExists =
                reviewRepository.existsByProductIdAndMemberIdAndDeletedAtIsNull(
                        reviewRequest.getProductId(),
                        loginMemberId
                );

        if (alreadyExists) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        ReviewEntity entity = ReviewEntity.builder()
                .productId(reviewRequest.getProductId())
                .memberId(loginMemberId)
                .content(reviewRequest.getContent())
                .rating(reviewRequest.getRating())
                .build();

        return ReviewResponse.from(reviewRepository.save(entity));
    }

    public List<ReviewResponse> getByProductId(Long productId) {
        return reviewRepository.findByProductId(productId)
                .stream()
                .map(ReviewResponse::from)
                .collect(Collectors.toList());
    }

    public ReviewResponse getByReviewId(Long reviewId) {
        ReviewEntity entity = reviewRepository.findById(reviewId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        return ReviewResponse.from(entity);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, Long loginMemberId, ReviewUpdateRequest request) {
        ReviewEntity entity = reviewRepository.findById(reviewId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        validateReviewOwner(entity, loginMemberId);

        entity.update(request.getContent(), request.getRating());

        return ReviewResponse.from(entity);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long loginMemberId) {
        ReviewEntity entity = reviewRepository.findById(reviewId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        validateReviewOwner(entity, loginMemberId);

        entity.softDelete();
    }

    private void validateReviewOwner(ReviewEntity review, Long loginMemberId) {
        if (loginMemberId == null || !review.getMemberId().equals(loginMemberId)) {
            throw new BusinessException(ErrorCode.REVIEW_FORBIDDEN);
        }
    }

}
