package org.example.groommvp.domain.category.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.category.entity.CategoryEntity;

@Getter
@Builder
public class CategoryChildResponse {

    private final Long categoryId;
    private final String categoryName;

    public static CategoryChildResponse from(CategoryEntity category) {
        return CategoryChildResponse.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .build();
    }
}
