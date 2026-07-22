package org.example.groommvp.domain.category.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.category.entity.CategoryEntity;

import java.util.List;

@Getter
@Builder
public class CategoryDetailResponse {

    private final Long categoryId;
    private final String categoryName;
    private final Long parentCategory;
    private final List<?> children;

    public static CategoryDetailResponse of(CategoryEntity category, List<?> children) {
        CategoryEntity parent = category.getParentCategory();
        return CategoryDetailResponse.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .parentCategory(parent == null ? null : parent.getCategoryId())
                .children(children)
                .build();
    }
}
