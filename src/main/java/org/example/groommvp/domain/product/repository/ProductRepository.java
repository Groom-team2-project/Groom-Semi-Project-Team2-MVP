package org.example.groommvp.domain.product.repository;

import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    //상품 이름으로 검색
    Page<ProductEntity> findByProductNameContaining(String keyword, Pageable pageable);

    List<ProductEntity> findAllByCategoryAndDeletedAtIsNullOrderByProductIdAsc(CategoryEntity category);

    //카테고리 삭제 전 연결 확인
    boolean existsByCategory(CategoryEntity category);

    //삭제되지 않은 상품만 연결
    boolean existsByCategoryAndDeletedAtIsNull(CategoryEntity category);
}
