package org.example.groommvp.domain.stock.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.dto.StockHistoryResponse;
import org.example.groommvp.domain.stock.dto.StockInRequest;
import org.example.groommvp.domain.stock.dto.StockResponse;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고/입고 비즈니스 로직 구현체.
 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    /**
     * 입고 처리.
     *
     * <p>흐름: 재고 레코드를 비관적 락으로 조회(없으면 생성) → 재고 증가 → 입고 히스토리 적재.
     * 동시 입고/구매로 인한 재고 정합성 문제를 막기 위해 {@code PESSIMISTIC_WRITE} 락을 건다.
     * 하나의 트랜잭션으로 묶여, 중간에 실패하면 재고 증가도 함께 롤백된다.
     */
    @Override
    @Transactional
    public StockHistoryResponse stockIn(Long productId, StockInRequest request) {
        // 재고 레코드를 락과 함께 조회, 없으면 상품을 확인하고 0개짜리 재고를 생성
        StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
                .orElseGet(() -> createStock(productId));

        // 재고 증가 (수량 유효성은 도메인에서 검증)
        stock.increase(request.getQuantity());

        // 입고 이력 적재 (입고는 INBOUND, 주문과 무관하므로 orderId 는 null)
        StockHistoryEntity history = StockHistoryEntity.inbound(
                stock, request.getQuantity(), request.getReason());
        StockHistoryEntity saved = stockHistoryRepository.save(history);

        return StockHistoryResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse getStock(Long productId) {
        StockEntity stock = stockRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        return StockResponse.from(stock);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockHistoryResponse> getHistories(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return stockHistoryRepository.findHistoriesByProductId(productId).stream()
                .map(StockHistoryResponse::from)
                .toList();
    }

    /** 재고 레코드가 없는 상품에 대해 최초 재고(0개)를 생성한다. 상품이 없으면 예외. */
    private StockEntity createStock(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return stockRepository.save(StockEntity.init(product));
    }
}
