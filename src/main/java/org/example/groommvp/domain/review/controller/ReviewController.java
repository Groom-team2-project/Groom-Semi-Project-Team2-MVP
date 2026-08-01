package org.example.groommvp.domain.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.review.dto.ReviewEligibilityResponse;
import org.example.groommvp.domain.review.dto.ReviewRequest;
import org.example.groommvp.domain.review.dto.ReviewResponse;
import org.example.groommvp.domain.review.dto.ReviewUpdateRequest;
import org.example.groommvp.domain.review.service.ReviewService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Review", description = "상품 리뷰 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(
            summary = "리뷰 등록",
            description = "결제가 완료된 상품의 구매자만 리뷰를 등록할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/reviews")
    public ResponseEntity<CommonResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody ReviewRequest request
    ) {
        ReviewResponse response =
                reviewService.createReview(request, authMember.memberId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "리뷰 등록 성공"));
    }

    @Operation(
            summary = "리뷰 작성 자격 확인",
            description = "로그인한 회원이 이 상품에 리뷰를 새로 작성할 수 있는 상태인지 확인합니다. " +
                    "비로그인/미구매/이미 리뷰 작성 상태면 eligible=false 로 응답합니다."
    )
    @GetMapping("/products/{productId}/reviews/eligibility")
    public ResponseEntity<CommonResponse<ReviewEligibilityResponse>> getReviewEligibility(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long productId
    ) {
        Long loginMemberId = authMember != null ? authMember.memberId() : null;
        ReviewEligibilityResponse response = reviewService.checkEligibility(loginMemberId, productId);

        return ResponseEntity.ok(
                CommonResponse.success(response, "리뷰 작성 자격 확인 성공")
        );
    }

    @Operation(
            summary = "상품별 리뷰 목록 조회",
            description = "특정 상품에 등록된 리뷰 목록을 조회합니다."
    )
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<CommonResponse<List<ReviewResponse>>> getReviewsByProduct(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long productId
    ) {
        List<ReviewResponse> response =
                reviewService.getByProductId(productId);

        return ResponseEntity.ok(
                CommonResponse.success(response, "상품 리뷰 목록 조회 성공")
        );
    }

    @Operation(
            summary = "리뷰 단건 조회",
            description = "리뷰 ID로 리뷰 한 건을 조회합니다."
    )
    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<CommonResponse<ReviewResponse>> getReview(
            @Parameter(description = "리뷰 ID", example = "1")
            @PathVariable Long reviewId
    ) {
        ReviewResponse response =
                reviewService.getByReviewId(reviewId);

        return ResponseEntity.ok(
                CommonResponse.success(response, "리뷰 조회 성공")
        );
    }

    @Operation(
            summary = "리뷰 수정",
            description = "본인이 작성한 리뷰의 내용과 평점을 수정합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<CommonResponse<ReviewResponse>> updateReview(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "리뷰 ID", example = "1")
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        ReviewResponse response = reviewService.updateReview(
                reviewId,
                authMember.memberId(),
                request
        );

        return ResponseEntity.ok(
                CommonResponse.success(response, "리뷰 수정 성공")
        );
    }

    @Operation(
            summary = "리뷰 삭제",
            description = "본인이 작성한 리뷰를 삭제합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal AuthMember authMember,
            @Parameter(description = "리뷰 ID", example = "1")
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(
                reviewId,
                authMember.memberId()
        );

        return ResponseEntity.noContent().build();
    }
}
