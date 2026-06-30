package org.example.groommvp.domain.stock.repository;

import org.example.groommvp.domain.stock.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {
}
