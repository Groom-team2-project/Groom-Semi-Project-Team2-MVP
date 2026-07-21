package org.example.groommvp.domain.category.service;

import org.example.groommvp.domain.category.dto.CategoryCreateRequest;
import org.example.groommvp.domain.category.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    //대분류 카테고리 생성
    CategoryResponse createLargeCategory(CategoryCreateRequest request);

    //중분류 카테고리 생성
    CategoryResponse createMiddleCategory(Long parentId, CategoryCreateRequest request);

    //대분류&중분류 카테고리 조회
    List<CategoryTreeResponse> getCategories();

}
