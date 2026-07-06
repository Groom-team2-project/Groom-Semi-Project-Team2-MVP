package org.example.groommvp.domain.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * ===== Repository 통합 테스트 =====
 *
 * <p>{@code @SpringBootTest} + {@code @Transactional} 로 테스트마다 자동 롤백한다.
 * (테스트 클래스패스의 H2 인메모리 DB 사용)
 * ProductEntity ↔ StockEntity ↔ StockHistoryEntity 연관관계 매핑과 JPQL 조회를 검증한다.
 */
@SpringBootTest
@Transactional
class StockHistoryRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Test
    @DisplayName("StockHistory 저장 시 stock 연관관계와 생성 시각이 정상 매핑된다")
    void save_and_association() {
        // given
        ProductEntity product = productRepository.save(new ProductEntity("후드티", 30000));
        StockEntity stock = stockRepository.save(new StockEntity(product, 20));
        StockHistoryEntity history = StockHistoryEntity.inbound(stock, 20, "신규 입고");

        // when
        StockHistoryEntity saved = stockHistoryRepository.save(history);

        // then
        assertThat(saved.getHistoryId()).isNotNull();
        assertThat(saved.getStock().getStockId()).isEqualTo(stock.getStockId());
        assertThat(saved.getChangeType()).isEqualTo(StockHistoryType.INBOUND);
        assertThat(saved.getChangedQty()).isEqualTo(20);
        assertThat(saved.getReason()).isEqualTo("신규 입고");
        assertThat(saved.getOrderId()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull(); // @CreationTimestamp 동작 확인
    }

    @Test
    @DisplayName("findHistoriesByProductId 는 해당 상품의 이력만 조회한다")
    void findHistoriesByProductId_onlyThatProduct() {
        // given: 대상 상품의 이력 2건
        ProductEntity product = productRepository.save(new ProductEntity("바지", 40000));
        StockEntity stock = stockRepository.save(new StockEntity(product, 0));
        stockHistoryRepository.save(StockHistoryEntity.inbound(stock, 5, "1차"));
        stockHistoryRepository.save(StockHistoryEntity.inbound(stock, 7, "2차"));

        // and: 다른 상품의 이력 1건 (조회되면 안 됨)
        ProductEntity other = productRepository.save(new ProductEntity("셔츠", 20000));
        StockEntity otherStock = stockRepository.save(new StockEntity(other, 0));
        stockHistoryRepository.save(StockHistoryEntity.inbound(otherStock, 3, "타상품"));

        // when
        List<StockHistoryEntity> histories =
                stockHistoryRepository.findHistoriesByProductId(product.getProductId());

        // then
        assertThat(histories).hasSize(2);
        assertThat(histories).allMatch(
                h -> h.getStock().getProduct().getProductId().equals(product.getProductId()));
    }
}
