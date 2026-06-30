package org.example.groommvp.domain.stock.repository;

import java.util.Optional;

import org.example.groommvp.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s join fetch s.product where s.product.product_id = :productId")
    Optional<Stock> findByProductIdWithPessimisticLock(@Param("productId") Long productId);
}
