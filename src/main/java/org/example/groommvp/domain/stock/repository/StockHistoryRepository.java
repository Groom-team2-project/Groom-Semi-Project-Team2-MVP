package org.example.groommvp.domain.stock.repository;

import java.util.List;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockHistoryRepository extends JpaRepository<StockHistoryEntity, Long> {

    /**
     * 특정 상품의 재고 변동 이력을 최신순으로 조회한다.
     *
     * <p>이력은 stock 을 거쳐 product 와 연결되므로 {@code h.stock.product} 로 조인한다.
     */
    @Query("select h from StockHistoryEntity h "
            + "where h.stock.product.productId = :productId "
            + "order by h.createdAt desc")
    List<StockHistoryEntity> findHistoriesByProductId(@Param("productId") Long productId);
}
