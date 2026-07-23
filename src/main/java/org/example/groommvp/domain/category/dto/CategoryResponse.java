package org.example.groommvp.domain.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.category.entity.CategoryEntity;

@Schema(description = "카테고리 상세 응답")
@Getter
@Builder
public class CategoryResponse {

    @Schema(description = "등록된 카테고리 id", example = "1")
    private final Long categoryId;

    @Schema(description = "등록된 카테고리명", example = "전자제품")
    private final String categoryName;

    @Schema(description = "상위 카테고리 id", example = "NULL")
    private final Long parentCategory;

    public static CategoryResponse from(CategoryEntity category) {
        CategoryEntity parent = category.getParentCategory();
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .parentCategory(parent == null ? null : parent.getCategoryId())
                .build();
    }
}
