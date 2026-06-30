package org.example.groommvp.domain.product.repository;

import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    //상품 이름으로 검색
    Page<ProductEntity> findByProductNameContaining(String keyword, Pageable pageable);
}
