package org.example.groommvp.domain.category.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.category.dto.CategoryCreateRequest;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
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

    //대분류 생성
    @Override
    @Transactional
    public CategoryResponse createLargeCategory(CategoryCreateRequest request) {
        CategoryEntity category = CategoryEntity.builder()
                .categoryName(request.getCategoryName().trim())
                .parentId(null)
                .build();
        CategoryEntity savedCategory = categoryRepository.save(category);
        return CategoryResponse.from(savedCategory);
    }

    //중분류 생성
    @Override
    @Transactional
    public CategoryResponse createMiddleCategory(Long parentId, CategoryCreateRequest request) {
        //부모 카테고리 여부 확인
        CategoryEntity parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        //부모 카테고리 맞는지 확인
        if (parent.getParentId() != null) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_CHILDREN);
        }
        //카테고리명 중복 확인
        String categoryName = request.getCategoryName().trim();
        if (categoryRepository.existsByCategoryNameAndParentCategory(categoryName, parent)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        CategoryEntity childCategory = CategoryEntity.builder()
                .categoryName(categoryName)
                .parentId(parent)
                .build();
        return CategoryResponse.from(categoryRepository.save(childCategory));
    }

    public List<CategoryTreeResponse> getCategories() {
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(()-> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));


    }
}
