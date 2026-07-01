package org.example.groommvp.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @NotBlank(message = "제품명을 입력하세요.")
    @Size(max = 50, message = "제품명은 최대 50자까지 입력 가능합니다.")
    private String productName;

    @NotNull(message = "제품 가격을 입력하세요.")
    @Positive(message = "제품 가격은 0보다 커야 합니다.")
    private Integer productPrice;
}
