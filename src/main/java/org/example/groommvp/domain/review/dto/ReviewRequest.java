package org.example.groommvp.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewRequest {
    @NotNull
    private Long productId;

    //@NotNull
    //private Long memberId;  로그인 아이디로 대체

    private String content;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;
}
