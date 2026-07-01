package org.example.groommvp.domain.stock.repository;

import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistoryEntity, Long> {
}
