package org.example.groommvp.domain.category.service;

import org.example.groommvp.domain.category.dto.CategoryDetailResponse;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.dto.ProductChildResponse;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImpTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImp categoryService;

    @Test
    void getLargeCategories_returnsOnlyLargeCategories() {
        CategoryEntity electronics = category(1L, "전자기기", null);
        CategoryEntity fruit = category(5L, "과일", null);
        when(categoryRepository.findAllByParentCategoryIsNullOrderByCategoryIdAsc())
                .thenReturn(List.of(electronics, fruit));

        List<CategoryResponse> result = categoryService.getLargeCategories();

        assertThat(result).extracting(CategoryResponse::getCategoryName)
                .containsExactly("전자기기", "과일");
        assertThat(result).allMatch(category -> category.getParentCategory() == null);
    }

    @Test
    void getCategory_returnsMiddleCategoriesForLargeCategory() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryEntity laptop = category(2L, "노트북", large);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(large));
        when(categoryRepository.findAllByParentCategoryOrderByCategoryIdAsc(large))
                .thenReturn(List.of(laptop));

        CategoryDetailResponse result = categoryService.getCategory(1L);

        assertThat(result.getParentCategory()).isNull();
        assertThat(result.getChildren()).hasSize(1);
    }

    @Test
    void getCategory_returnsActiveProductsForMiddleCategory() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryEntity middle = category(2L, "노트북", large);
        ProductEntity macbook = ProductEntity.builder()
                .productName("맥북")
                .productPrice(2_500_000)
                .category(middle)
                .build();
        ReflectionTestUtils.setField(macbook, "productId", 10L);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(middle));
        when(productRepository.findAllByCategoryAndDeletedAtIsNullOrderByProductIdAsc(middle))
                .thenReturn(List.of(macbook));

        CategoryDetailResponse result = categoryService.getCategory(2L);

        assertThat(result.getParentCategory()).isEqualTo(1L);
        assertThat(result.getChildren()).singleElement()
                .isInstanceOfSatisfying(ProductChildResponse.class,
                        product -> assertThat(product.getProductName()).isEqualTo("맥북"));
    }

    @Test
    void getCategory_throwsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private CategoryEntity category(Long id, String name, CategoryEntity parent) {
        CategoryEntity category = CategoryEntity.builder()
                .categoryName(name)
                .parentCategory(parent)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }
}
