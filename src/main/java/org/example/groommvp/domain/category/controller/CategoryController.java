package org.example.groommvp.domain.category.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.category.dto.CategoryCreateRequest;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.dto.CategoryUpdateRequest;
import org.example.groommvp.domain.category.dto.CategoryDetailResponse;
import org.example.groommvp.domain.category.service.CategoryService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Category", description = "카테고리 API")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    //대분류 카테고리 생성
    @PostMapping
    public ResponseEntity<CommonResponse<CategoryResponse>> createLargeCategory(
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.createLargeCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "카테고리가 생성되었습니다."));
    }

    //중분류 카테고리 생성
    @PostMapping("/{parentId}/children")
    public ResponseEntity<CommonResponse<CategoryResponse>> createMiddleCategory(
            @PathVariable Long parentId,
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.createMiddleCategory(parentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "카테고리가 생성되었습니다."));
    }

    //카테고리 수정
    @PutMapping("/{categoryId}")
    public ResponseEntity<CommonResponse<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        CategoryResponse response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(response, "카테고리 수정 성공"));
    }

    //카테고리 삭제
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<CommonResponse<CategoryResponse>> deleteCategory(
            @PathVariable Long categoryId) {
        CategoryResponse response = categoryService.deleteCategory(categoryId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(response, "카테고리 삭제 성공"));
    }

    //대분류 카테고리 조회
    @GetMapping
    public ResponseEntity<CommonResponse<List<CategoryResponse>>> getLargeCategories() {
        List<CategoryResponse> response = categoryService.getLargeCategories();
        return ResponseEntity.ok(CommonResponse.success(response, "카테고리 조회 성공"));
    }

    //카테고리 상세 조회: 대분류는 중분류, 중분류는 상품을 children으로 반환
    @GetMapping("/{categoryId}")
    public ResponseEntity<CommonResponse<CategoryDetailResponse>> getCategory(
            @PathVariable Long categoryId) {
        CategoryDetailResponse response = categoryService.getCategory(categoryId);
        return ResponseEntity.ok(CommonResponse.success(response, "카테고리 상세 조회 성공"));
    }
}
