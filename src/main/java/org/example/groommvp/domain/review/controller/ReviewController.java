package org.example.groommvp.domain.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.review.dto.ReviewRequest;
import org.example.groommvp.domain.review.dto.ReviewResponse;
import org.example.groommvp.domain.review.dto.ReviewUpdateRequest;
import org.example.groommvp.domain.review.service.ReviewService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Review", description = "상품 리뷰 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(
            summary = "리뷰 등록",
            description = "상품 리뷰를 등록합니다."
    )
    @PostMapping("/reviews")
    public ResponseEntity<CommonResponse<ReviewResponse>> createReview(
            //@AuthenticationPrincipal AuthMember authMember,  auth 연결후 주석 제거 예정
            @RequestParam(name = "memberId") Long memberId,
            @Valid @RequestBody ReviewRequest request
    ) {
        ReviewResponse response =
                //reviewService.createReview(request, authMember.memberId());
                reviewService.createReview(request, memberId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "리뷰 등록 성공"));
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
            description = "본인이 작성한 리뷰의 내용과 평점을 수정합니다."
    )
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<CommonResponse<ReviewResponse>> updateReview(
            //@AuthenticationPrincipal AuthMember authMember, , auth 연결후 주석 제거 예정
            @RequestParam Long memberId,
            @Parameter(description = "리뷰 ID", example = "1")
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        ReviewResponse response = reviewService.updateReview(
                reviewId,
                memberId, // authMember.memberId(),
                request
        );

        return ResponseEntity.ok(
                CommonResponse.success(response, "리뷰 수정 성공")
        );
    }

    @Operation(
            summary = "리뷰 삭제",
            description = "본인이 작성한 리뷰를 삭제합니다."
    )
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            //@AuthenticationPrincipal AuthMember authMember, auth 연결후 주석 제거 예정
            @RequestParam Long memberId,
            @Parameter(description = "리뷰 ID", example = "1")
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(
                reviewId,
                memberId //authMember.memberId()

        );

        return ResponseEntity.noContent().build();
    }
}
