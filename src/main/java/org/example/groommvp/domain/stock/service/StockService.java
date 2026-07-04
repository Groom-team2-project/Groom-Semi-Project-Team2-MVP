package org.example.groommvp.domain.stock.service;

import java.util.List;
import org.example.groommvp.domain.stock.dto.StockHistoryResponse;
import org.example.groommvp.domain.stock.dto.StockInRequest;

/**
 * 재고/입고 비즈니스 로직 인터페이스.
 */
public interface StockService {

    /**
     * 상품을 입고 처리한다. 상품의 재고를 늘리고 입고 히스토리를 한 건 적재한다.
     *
     * @param productId 입고할 상품 ID
     * @param request   입고 수량/비고
     * @return 생성된 입고 히스토리
     */
    StockHistoryResponse stockIn(Long productId, StockInRequest request);

    /** 특정 상품의 재고 변동 이력을 최신순으로 조회한다. */
    List<StockHistoryResponse> getHistories(Long productId);
}
