package org.example.groommvp.domain.product.repository;

import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findAllByCategoryAndDeletedAtIsNullOrderByProductIdAsc(CategoryEntity category);

    //카테고리 삭제 전 연결 확인
    boolean existsByCategory(CategoryEntity category);

    //일반 목록 조회 (최신순/조회수순/가격순, keyword/categoryId는 null이면 조건 무시)
    @Query(value = """
            select p from ProductEntity p
            where p.deletedAt is null
              and (:keyword is null or p.productName like concat('%', :keyword, '%'))
              and (:categoryId is null or p.category.categoryId = :categoryId)
            """,
            countQuery = """
            select count(p) from ProductEntity p
            where p.deletedAt is null
              and (:keyword is null or p.productName like concat('%', :keyword, '%'))
              and (:categoryId is null or p.category.categoryId = :categoryId)
            """)
    Page<ProductEntity> findAllByKeywordAndCategory(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );


    // 인기순 정렬 (결제완료 주문건수 기준)
    @Query(value = """
            select p from ProductEntity p
            left join OrderItem oi on oi.product = p and oi.order.status = :completedStatus
            where p.deletedAt is null
              and (:keyword is null or p.productName like concat('%', :keyword, '%'))
              and (:categoryId is null or p.category.categoryId = :categoryId)
            group by p
            order by count(oi) desc, p.productId desc
            """,
            countQuery = """
            select count(distinct p) from ProductEntity p
            where p.deletedAt is null
              and (:keyword is null or p.productName like concat('%', :keyword, '%'))
              and (:categoryId is null or p.category.categoryId = :categoryId)
            """)
    Page<ProductEntity> findAllOrderByCompletedOrderCountDesc(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("completedStatus") OrderStatus completedStatus,
            Pageable pageable
    );

    @Modifying
    @Query("update ProductEntity p set p.viewCount = p.viewCount + 1 where p.productId = :productId")
    void incrementViewCount(@Param("productId") Long productId);

}
