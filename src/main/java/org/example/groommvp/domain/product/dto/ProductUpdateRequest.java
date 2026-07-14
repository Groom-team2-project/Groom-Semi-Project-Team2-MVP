package org.example.groommvp.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "상품 수정 요청")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @Schema(description = "변경할 상품명 (최대 50자)", example = "MacBook Pro 14인치",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "제품명을 입력하세요.")
    @Size(max = 50, message = "제품명은 최대 50자까지 입력 가능합니다.")
    private String productName;

    @Schema(description = "변경할 상품 가격 (1 이상)", example = "2800000",
            minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "제품 가격을 입력하세요.")
    @Positive(message = "제품 가격은 0보다 커야 합니다.")
    private Integer productPrice;
}
