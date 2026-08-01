package org.example.groommvp.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.review.dto.ReviewRequest;
import org.example.groommvp.domain.review.entity.ReviewEntity;
import org.example.groommvp.domain.review.repository.ReviewRepository;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MemberRepository SmemberRepository;

    @InjectMocks
    private ReviewService reviewService;

    private static ReviewRequest reviewRequest(Long productId, String content, Integer rating) {
        ReviewRequest request = new ReviewRequest();
        ReflectionTestUtils.setField(request, "productId", productId);
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "rating", rating);
        return request;
    }

    @Nested
    @DisplayName("리뷰 등록 - 구매 이력 검증")
    class CreateReviewPurchaseValidation {

        @Test
        @DisplayName("결제완료 상태로 구매한 회원은 리뷰를 등록할 수 있다")
        void createReview_success_whenMemberCompletedPurchase() {
            ReviewRequest request = reviewRequest(PRODUCT_ID, "좋아요", 5);

            given(orderItemRepository.existsByMemberIdAndProductIdAndOrderStatus(
                    MEMBER_ID, PRODUCT_ID, OrderStatus.COMPLETED))
                    .willReturn(true);
            given(reviewRepository.existsByProductIdAndMemberIdAndDeletedAtIsNull(PRODUCT_ID, MEMBER_ID))
                    .willReturn(false);

            ReviewEntity saved = ReviewEntity.builder()
                    .productId(PRODUCT_ID)
                    .memberId(MEMBER_ID)
                    .content("좋아요")
                    .rating(5)
                    .build();
            given(reviewRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ReviewEntity.class)))
                    .willReturn(saved);

            var response = reviewService.createReview(request, MEMBER_ID);

            assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(response.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(response.getRating()).isEqualTo(5);
        }

        @Test
        @DisplayName("결제완료 이력이 없는 회원은 리뷰를 등록할 수 없다")
        void createReview_throws_whenNotPurchased() {
            ReviewRequest request = reviewRequest(PRODUCT_ID, "좋아요", 5);

            given(orderItemRepository.existsByMemberIdAndProductIdAndOrderStatus(
                    MEMBER_ID, PRODUCT_ID, OrderStatus.COMPLETED))
                    .willReturn(false);

            assertThatThrownBy(() -> reviewService.createReview(request, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_PURCHASE_REQUIRED);

            verify(reviewRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("결제완료 이력은 있지만 이미 리뷰를 작성한 경우 다시 작성할 수 없다")
        void createReview_throws_whenReviewAlreadyExists() {
            ReviewRequest request = reviewRequest(PRODUCT_ID, "좋아요", 5);

            given(orderItemRepository.existsByMemberIdAndProductIdAndOrderStatus(
                    MEMBER_ID, PRODUCT_ID, OrderStatus.COMPLETED))
                    .willReturn(true);
            given(reviewRepository.existsByProductIdAndMemberIdAndDeletedAtIsNull(PRODUCT_ID, MEMBER_ID))
                    .willReturn(true);

            assertThatThrownBy(() -> reviewService.createReview(request, MEMBER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_ALREADY_EXISTS);

            verify(reviewRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("로그인하지 않은 상태(memberId가 없음)면 리뷰를 등록할 수 없다")
        void createReview_throws_whenLoginMemberIdIsNull() {
            ReviewRequest request = reviewRequest(PRODUCT_ID, "좋아요", 5);

            assertThatThrownBy(() -> reviewService.createReview(request, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

            verify(orderItemRepository, never())
                    .existsByMemberIdAndProductIdAndOrderStatus(
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any());
            verify(reviewRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        }
    }
}