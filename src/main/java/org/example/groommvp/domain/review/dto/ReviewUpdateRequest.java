package org.example.groommvp.domain.review.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ReviewUpdateRequest {

    @Size(max = 100, message = "리뷰 내용은 100자 이하여야 합니다.")
    private String content;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;
}
