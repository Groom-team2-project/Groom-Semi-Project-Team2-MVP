package org.example.groommvp.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.review.dto.ReviewEligibilityResponse;
import org.example.groommvp.domain.review.dto.ReviewRequest;
import org.example.groommvp.domain.review.dto.ReviewResponse;
import org.example.groommvp.domain.review.dto.ReviewUpdateRequest;
import org.example.groommvp.domain.review.entity.ReviewEntity;
import org.example.groommvp.domain.review.repository.ReviewRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ReviewResponse createReview(ReviewRequest reviewRequest, Long loginMemberId) {
        Long productId = reviewRequest.getProductId();

        //해당 회원이 이 상품을 결제 완료했는지 확인
        validateCompletedPayment(loginMemberId, productId);

        // 삭제되지 않은 기존 리뷰가 있는지 확인
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

        try {
            return toResponse(
                    reviewRepository.saveAndFlush(entity)
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getByProductId(
            Long productId
    ) {
        List<ReviewEntity> reviews =
                reviewRepository
                        .findByProductIdAndDeletedAtIsNull(
                                productId
                        );

        List<Long> memberIds = reviews.stream()
                .map(ReviewEntity::getMemberId)
                .distinct()
                .toList();

        Map<Long, String> nicknameByMemberId =
                memberRepository.findAllById(memberIds)
                        .stream()
                        .collect(Collectors.toMap(
                                MemberEntity::getMemberId,
                                member -> maskNickname(
                                        member.getNickname()
                                )
                        ));

        return reviews.stream()
                .map(review -> ReviewResponse.from(
                        review,
                        nicknameByMemberId.getOrDefault(
                                review.getMemberId(),
                                "익명**"
                        )
                ))
                .toList();
    }

    public ReviewResponse getByReviewId(Long reviewId) {
        ReviewEntity entity = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        return toResponse(entity);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, Long loginMemberId, ReviewUpdateRequest request) {
        ReviewEntity entity = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        validateReviewOwner(entity, loginMemberId);

        entity.update(request.getContent(), request.getRating());

        return toResponse(entity);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long loginMemberId) {
        ReviewEntity entity = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        validateReviewOwner(entity, loginMemberId);

        entity.softDelete();
    }

    private void validateReviewOwner(
            ReviewEntity review,
            Long loginMemberId
    ) {
        if (!Objects.equals(
                review.getMemberId(),
                loginMemberId
        )) {
            throw new BusinessException(
                    ErrorCode.REVIEW_FORBIDDEN
            );
        }
    }

    // 구매자 + 결제완료(COMPLETED) 상태인 주문이 있는 경우에만 리뷰 작성 허용
    private void validateCompletedPayment(Long loginMemberId, Long productId) {
        if (loginMemberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        boolean purchased = orderItemRepository.existsByMemberIdAndProductIdAndOrderStatus(
                loginMemberId,
                productId,
                OrderStatus.COMPLETED
        );

        if (!purchased) {
            throw new BusinessException(ErrorCode.REVIEW_PURCHASE_REQUIRED);
        }
    }

    // 로그인한 회원이 리뷰를 작성 할 수 있는지 확인한다.
    public ReviewEligibilityResponse checkEligibility(Long loginMemberId, Long productId) {
        if (loginMemberId == null) {
            return ReviewEligibilityResponse.builder().eligible(false).build();
        }

        boolean purchased = orderItemRepository.existsByMemberIdAndProductIdAndOrderStatus(
                loginMemberId, productId, OrderStatus.COMPLETED);
        if (!purchased) {
            return ReviewEligibilityResponse.builder().eligible(false).build();
        }

        boolean alreadyReviewed = reviewRepository.existsByProductIdAndMemberIdAndDeletedAtIsNull(
                productId, loginMemberId);

        return ReviewEligibilityResponse.builder().eligible(!alreadyReviewed).build();
    }

    private ReviewResponse toResponse(
            ReviewEntity review
    ) {
        String writerNickname =
                memberRepository
                        .findById(review.getMemberId())
                        .map(MemberEntity::getNickname)
                        .map(this::maskNickname)
                        .orElse("익명**");

        return ReviewResponse.from(
                review,
                writerNickname
        );
    }

    private String maskNickname(String nickname) {
        if (
                nickname == null ||
                        nickname.isBlank()
        ) {
            return "익명**";
        }

        String trimmed = nickname.trim();

        int visibleLength = Math.min(
                2,
                trimmed.codePointCount(
                        0,
                        trimmed.length()
                )
        );

        int endIndex = trimmed.offsetByCodePoints(
                0,
                visibleLength
        );

        return trimmed.substring(0, endIndex) + "**";
    }
}
