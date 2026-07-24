package org.example.groommvp.domain.category.service;

import org.example.groommvp.domain.category.dto.CategoryCreateRequest;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.dto.CategoryUpdateRequest;
import org.example.groommvp.domain.category.dto.CategoryDetailResponse;

import java.util.List;

public interface CategoryService {

    //대분류 카테고리 생성
    CategoryResponse createLargeCategory(CategoryCreateRequest request);

    //중분류 카테고리 생성
    CategoryResponse createMiddleCategory(Long parentId, CategoryCreateRequest request);

    //카테고리 수정
    CategoryResponse updateCategory(Long categoryId, CategoryUpdateRequest request);

    //카테고리 삭제
    CategoryResponse deleteCategory(Long categoryId);

    //대분류 카테고리 조회
    List<CategoryResponse> getLargeCategories();

    //카테고리 목록 조회
    CategoryDetailResponse getCategory(Long categoryId);
}
