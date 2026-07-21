package org.example.groommvp.domain.category.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.category.dto.CategoryCreateRequest;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.service.CategoryService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Category", description = "카테고리 API")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    //대분류 카테고리 생성
    @PostMapping
    public ResponseEntity<CommonResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.createLargeCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "카테고리가 생성되었습니다."));
    }

    @PostMapping("/{parentId}/children")
    public ResponseEntity<CommonResponse<CategoryResponse>> createCategory(
            @PathVariable Long parentId,
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.createMiddleCategory(parentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "카테고리가 생성되었습니다."));
    }
}
