package org.example.groommvp.domain.category.repository;

import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    //카테고리명 중복 확인
    boolean existsByCategoryName(String categoryName);

    //자신 제외 카테고리명 중복 확인
    boolean existsByCategoryNameAndCategoryIdNot(String categoryName, Long categoryId);

    List<CategoryEntity> findAllByParentCategoryIsNullOrderByCategoryIdAsc();

    List<CategoryEntity> findAllByParentCategoryOrderByCategoryIdAsc(CategoryEntity parentCategory);

    //카테고리 삭제 전 연결 확인
    boolean existsByParentCategory(CategoryEntity parentCategory);
}
