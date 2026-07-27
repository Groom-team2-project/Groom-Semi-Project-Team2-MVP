package org.example.groommvp.domain.category.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Getter
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_id",
                columnNames = "category_name"
        )
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
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
            CategoryEntity parentCategory
    ) {
        this.categoryName = categoryName;
        this.parentCategory = parentCategory;
    }

    public void update(
            String categoryName
    ) {
        this.categoryName = categoryName;
    }
}
