package org.example.groommvp.domain.category.repository;

import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    boolean existsByCategoryNameAndParentCategoryIsNull(String categoryName);

    boolean existsByCategoryNameAndParentCategory(String categoryName, CategoryEntity parentCategory);
}
