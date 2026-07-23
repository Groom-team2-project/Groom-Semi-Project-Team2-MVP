package org.example.groommvp.domain.category.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.category.dto.*;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryServiceImp implements CategoryService{

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    //대분류 생성
    @Override
    @Transactional
    public CategoryResponse createLargeCategory(CategoryCreateRequest request) {
        //카테고리명 중복 확인
        String categoryName = request.getCategoryName();
        if (categoryRepository.existsByCategoryName(categoryName)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        CategoryEntity category = CategoryEntity.builder()
                .categoryName(request.getCategoryName().trim())
                .parentCategory(null)
                .build();
        CategoryEntity savedCategory = categoryRepository.save(category);
        return CategoryResponse.from(savedCategory);
    }

    //중분류 생성
    @Override
    @Transactional
    public CategoryResponse createMiddleCategory(Long parentId, CategoryCreateRequest request) {
        //카테고리명 중복 확인
        String categoryName = request.getCategoryName();
        if (categoryRepository.existsByCategoryName(categoryName)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        //상위 카테고리 인자값 없을 때
        if (parentId == null) {
            throw new BusinessException(ErrorCode.PARENT_CATEGORY_MISSING);
        }
        //상위 카테고리 찾을 수 없을 때
        CategoryEntity parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        //상위 카테고리가 대분류인지 확인
        if (parent.getParentCategory() != null) {
            throw new BusinessException(ErrorCode.INVALID_PARENT_CATEGORY);
        }

        CategoryEntity childCategory = CategoryEntity.builder()
                .categoryName(categoryName)
                .parentCategory(parent)
                .build();
        return CategoryResponse.from(categoryRepository.save(childCategory));
    }

    //카테고리 수정
    @Override
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryUpdateRequest request) {
        String categoryName = request.getCategoryName();
        //카테고리 찾을 수 없을 때
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(()->new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        //카테고리명 중복 확인
        if (categoryRepository.existsByCategoryNameAndCategoryIdNot(categoryName, categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        category.update(categoryName);
        return CategoryResponse.from(category);
    }

    //카테고리 삭제
    @Override
    @Transactional
    public CategoryResponse deleteCategory(Long categoryId) {
        //카테고리 찾을 수 없을 때
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(()->new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        //대분류 카테고리
        if (category.getParentCategory() == null) {
            if (categoryRepository.existsByParentCategory(category)) {
                throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
            }
        }//중분류 카테고리
        else {
            if (productRepository.existsByCategory(category)) {
                throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);
            }
        }

        CategoryResponse response = CategoryResponse.from(category);
        categoryRepository.delete(category);

        return response;
    }

    //대분류 카테고리 조회
    @Override
    public List<CategoryResponse> getLargeCategories() {
        List<CategoryResponse> categories = categoryRepository
                .findAllByParentCategoryIsNullOrderByCategoryIdAsc()
                .stream()
                .map(CategoryResponse::from)
                .toList();

        if (categories.isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return categories;
    }

    //카테고리 목록 조회
    @Override
    public CategoryDetailResponse getCategory(Long categoryId) {
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(()-> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        //대분류의 중분류
        if (category.getParentCategory() == null) {
            List<CategoryChildResponse> children = categoryRepository
                    .findAllByParentCategoryOrderByCategoryIdAsc(category)
                    .stream()
                    .map(CategoryChildResponse::from)
                    .toList();

            if (children.isEmpty()) {
                throw new BusinessException(ErrorCode.CONTENT_NOT_FOUND);
            }

            return CategoryDetailResponse.of(category, children);
        }

        //중분류의 상품
        List<ProductChildResponse> children = productRepository
                .findAllByCategoryAndDeletedAtIsNullOrderByProductIdAsc(category)
                .stream()
                .map(ProductChildResponse::from)
                .toList();

        if (children.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_NOT_FOUND);
        }

        return CategoryDetailResponse.of(category, children);
    }
}
