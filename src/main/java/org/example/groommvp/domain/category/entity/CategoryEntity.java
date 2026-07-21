package org.example.groommvp.domain.category.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;


@Entity
@Getter
@Table(name = "categories")
public class CategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId; //모든 카테고리행의 고유 ID

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName; //카테고리 이름

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category")
    private CategoryEntity parentCategory; //현재 카테고리가 속한 대분류(대분류 == null, 중분류 != null)

    @Builder
    public CategoryEntity(
            String categoryName,
            CategoryEntity parentId
    ) {
        this.categoryName = categoryName;
        this.parentCategory = parentCategory;
    }
}