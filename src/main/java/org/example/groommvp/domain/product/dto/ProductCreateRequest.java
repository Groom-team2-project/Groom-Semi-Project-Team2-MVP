package org.example.groommvp.domain.product.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "상품 등록 요청")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @Schema(description = "상품명 (최대 50자)", example = "MacBook Pro",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "제품명을 입력하세요.")
    @Size(max = 50, message = "제품명은 최대 50자까지 입력 가능합니다.")
    private String productName;

    @Schema(description = "상품 가격 (1 이상)", example = "2500000",
            minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "제품 가격을 입력하세요.")
    @Positive(message = "제품 가격은 0보다 커야 합니다.")
    private Integer productPrice;

    @Schema(description = "초기 재고 수량 (1 이상)", example = "100",
            minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "등록할 제품의 수량을 입력하세요.")
    @Min(value = 0, message = "0개 이상")
    private Integer stocks;
}
