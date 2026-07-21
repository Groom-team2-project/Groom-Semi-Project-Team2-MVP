package org.example.groommvp.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "상품 이미지 등록 요청")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ImageCreateRequest {

    @Schema(description = "상품 이미지")
    @NotBlank(message = "상품 이미지를 등록하세요.")
    private String imageUrl;
}
