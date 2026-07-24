package org.example.groommvp.domain.review.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.review.entity.ReviewEntity;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewResponse {

    private Long reviewId;
    private Long productId;
    private Long memberId;
    private String content;
    private Integer rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReviewResponse from(ReviewEntity entity) {
        return ReviewResponse.builder()
                .reviewId(entity.getReviewId())
                .productId(entity.getProductId())
                .memberId(entity.getMemberId())
                .content(entity.getContent())
                .rating(entity.getRating())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}
