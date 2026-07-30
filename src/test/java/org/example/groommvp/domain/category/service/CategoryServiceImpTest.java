package org.example.groommvp.domain.category.service;

import org.example.groommvp.domain.category.dto.CategoryDetailResponse;
import org.example.groommvp.domain.category.dto.CategoryCreateRequest;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.dto.CategoryUpdateRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImpTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createLargeCategory_createsCategoryWithoutParent() {
        CategoryCreateRequest request = new CategoryCreateRequest(" 전자기기 ");
        when(categoryRepository.existsByCategoryName("전자기기")).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class)))
                .thenAnswer(invocation -> {
                    CategoryEntity saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "categoryId", 1L);
                    return saved;
                });

        CategoryResponse result = categoryService.createLargeCategory(request);

        assertThat(result.getCategoryId()).isEqualTo(1L);
        assertThat(result.getCategoryName()).isEqualTo("전자기기");
        assertThat(result.getParentCategory()).isNull();
    }

    @Test
    void createLargeCategory_throwsWhenNameIsDuplicated() {
        CategoryCreateRequest request = new CategoryCreateRequest("전자기기");
        when(categoryRepository.existsByCategoryName("전자기기")).thenReturn(true);

        assertBusinessException(
                () -> categoryService.createLargeCategory(request),
                ErrorCode.CATEGORY_NAME_DUPLICATED
        );
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createMiddleCategory_createsCategoryUnderLargeCategory() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryCreateRequest request = new CategoryCreateRequest("노트북");
        when(categoryRepository.existsByCategoryName("노트북")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(large));
        when(categoryRepository.save(any(CategoryEntity.class)))
                .thenAnswer(invocation -> {
                    CategoryEntity saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "categoryId", 2L);
                    return saved;
                });

        CategoryResponse result = categoryService.createMiddleCategory(1L, request);

        assertThat(result.getCategoryName()).isEqualTo("노트북");
        assertThat(result.getParentCategory()).isEqualTo(1L);
    }

    @Test
    void createMiddleCategory_throwsWhenNameIsDuplicated() {
        CategoryCreateRequest request = new CategoryCreateRequest("노트북");
        when(categoryRepository.existsByCategoryName("노트북")).thenReturn(true);

        assertBusinessException(
                () -> categoryService.createMiddleCategory(1L, request),
                ErrorCode.CATEGORY_NAME_DUPLICATED
        );
        verify(categoryRepository, never()).findById(any());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createMiddleCategory_throwsWhenParentDoesNotExist() {
        CategoryCreateRequest request = new CategoryCreateRequest("노트북");
        when(categoryRepository.existsByCategoryName("노트북")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> categoryService.createMiddleCategory(999L, request),
                ErrorCode.CATEGORY_NOT_FOUND
        );
    }

    @Test
    void createMiddleCategory_throwsWhenParentIsMiddleCategory() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryEntity middle = category(2L, "노트북", large);
        CategoryCreateRequest request = new CategoryCreateRequest("게이밍 노트북");
        when(categoryRepository.existsByCategoryName("게이밍 노트북")).thenReturn(false);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(middle));

        assertBusinessException(
                () -> categoryService.createMiddleCategory(2L, request),
                ErrorCode.INVALID_PARENT_CATEGORY
        );
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_updatesName() {
        CategoryEntity category = category(1L, "전자기기", null);
        CategoryUpdateRequest request = new CategoryUpdateRequest(" 디지털기기 ");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByCategoryNameAndCategoryIdNot("디지털기기", 1L))
                .thenReturn(false);
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse result = categoryService.updateCategory(1L, request);

        assertThat(result.getCategoryName()).isEqualTo("디지털기기");
        assertThat(category.getCategoryName()).isEqualTo("디지털기기");
    }

    @Test
    void updateCategory_throwsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> categoryService.updateCategory(999L, new CategoryUpdateRequest("전자기기")),
                ErrorCode.CATEGORY_NOT_FOUND
        );
    }

    @Test
    void updateCategory_throwsWhenNameBelongsToAnotherCategory() {
        CategoryEntity category = category(1L, "전자기기", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByCategoryNameAndCategoryIdNot("생활가전", 1L))
                .thenReturn(true);

        assertBusinessException(
                () -> categoryService.updateCategory(1L, new CategoryUpdateRequest("생활가전")),
                ErrorCode.CATEGORY_NAME_DUPLICATED
        );
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategory_deletesEmptyLargeCategory() {
        CategoryEntity large = category(1L, "전자기기", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(large));
        when(categoryRepository.existsByParentCategory(large)).thenReturn(false);

        CategoryResponse result = categoryService.deleteCategory(1L);

        assertThat(result.getCategoryId()).isEqualTo(1L);
        verify(categoryRepository).delete(large);
        verify(productRepository, never()).existsByCategory(any());
    }

    @Test
    void deleteCategory_throwsWhenLargeCategoryHasChildren() {
        CategoryEntity large = category(1L, "전자기기", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(large));
        when(categoryRepository.existsByParentCategory(large)).thenReturn(true);

        assertBusinessException(
                () -> categoryService.deleteCategory(1L),
                ErrorCode.CATEGORY_HAS_CHILDREN
        );
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategory_deletesMiddleCategoryWithoutProducts() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryEntity middle = category(2L, "노트북", large);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(middle));
        when(productRepository.existsByCategory(middle)).thenReturn(false);

        CategoryResponse result = categoryService.deleteCategory(2L);

        assertThat(result.getParentCategory()).isEqualTo(1L);
        verify(categoryRepository).delete(middle);
    }

    @Test
    void deleteCategory_throwsWhenMiddleCategoryHasProducts() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryEntity middle = category(2L, "노트북", large);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(middle));
        when(productRepository.existsByCategory(middle)).thenReturn(true);

        assertBusinessException(
                () -> categoryService.deleteCategory(2L),
                ErrorCode.CATEGORY_HAS_PRODUCTS
        );
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategory_throwsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> categoryService.deleteCategory(999L),
                ErrorCode.CATEGORY_NOT_FOUND
        );
    }

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
    void getLargeCategories_throwsWhenNoLargeCategoryExists() {
        when(categoryRepository.findAllByParentCategoryIsNullOrderByCategoryIdAsc())
                .thenReturn(List.of());

        assertBusinessException(
                categoryService::getLargeCategories,
                ErrorCode.CATEGORY_NOT_FOUND
        );
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

        assertBusinessException(
                () -> categoryService.getCategory(999L),
                ErrorCode.CATEGORY_NOT_FOUND
        );
    }

    @Test
    void getCategory_throwsWhenLargeCategoryHasNoChildren() {
        CategoryEntity large = category(1L, "전자기기", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(large));
        when(categoryRepository.findAllByParentCategoryOrderByCategoryIdAsc(large))
                .thenReturn(List.of());

        assertBusinessException(
                () -> categoryService.getCategory(1L),
                ErrorCode.CONTENT_NOT_FOUND
        );
    }

    @Test
    void getCategory_throwsWhenMiddleCategoryHasNoProducts() {
        CategoryEntity large = category(1L, "전자기기", null);
        CategoryEntity middle = category(2L, "노트북", large);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(middle));
        when(productRepository.findAllByCategoryAndDeletedAtIsNullOrderByProductIdAsc(middle))
                .thenReturn(List.of());

        assertBusinessException(
                () -> categoryService.getCategory(2L),
                ErrorCode.CONTENT_NOT_FOUND
        );
    }

    private void assertBusinessException(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
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
