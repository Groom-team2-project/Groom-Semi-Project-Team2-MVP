package org.example.groommvp.domain.stock.repository;

import java.util.Optional;

import org.example.groommvp.domain.stock.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StockRepository extends JpaRepository<StockEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockEntity s join fetch s.product where s.product.productId = :productId")
    Optional<StockEntity> findByProductIdWithPessimisticLock(@Param("productId") Long productId);
}
