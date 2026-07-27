package org.example.groommvp.domain.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "카테고리 수정 요청")
@Getter
@NoArgsConstructor
public class CategoryUpdateRequest {

    @Schema(description = "카테고리명", example = "전자제품")
    @NotBlank(message = "카테고리명을 입력하세요.")
    @Size(max = 50, message = "카테고리명은 최대 50자까지 입력 가능합니다.")
    private String categoryName;

    public CategoryUpdateRequest(String categoryName) {
        this.categoryName = normalize(categoryName);
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = normalize(categoryName);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
