package org.example.groommvp.domain.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.dto.StockHistoryResponse;
import org.example.groommvp.domain.stock.dto.StockInRequest;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryType;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ===== 서비스 단위 테스트 템플릿 =====
 *
 * <p>스프링 컨텍스트를 띄우지 않고({@code @ExtendWith(MockitoExtension.class)}) 협력 객체를 Mock 으로
 * 대체해 서비스의 비즈니스 로직만 빠르게 검증한다. (DB 불필요)
 *
 * <p>패턴: <b>given(준비) - when(실행) - then(검증)</b>
 */
@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @InjectMocks
    private StockServiceImpl stockService;

    @Test
    @DisplayName("입고에 성공하면 재고가 증가하고 입고 히스토리가 반환된다")
    void stockIn_success() {
        // given
        Long productId = 1L;
        ProductEntity product = product(productId, "티셔츠", 10000);
        StockEntity stock = StockEntity.builder().product(product).stocks(5).build();
        StockInRequest request = createRequest(10, "정기 입고");

        given(stockRepository.findByProductIdWithPessimisticLock(productId)).willReturn(Optional.of(stock));
        given(stockHistoryRepository.save(any(StockHistoryEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        StockHistoryResponse response = stockService.stockIn(productId, request);

        // then
        assertThat(stock.getStocks()).isEqualTo(15); // 5 + 10
        assertThat(response.getType()).isEqualTo(StockHistoryType.INBOUND);
        assertThat(response.getChangedQty()).isEqualTo(10);
        assertThat(response.getCurrentStocks()).isEqualTo(15);
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getReason()).isEqualTo("정기 입고");
        assertThat(response.getOrderId()).isNull(); // 입고는 주문과 무관
        verify(stockHistoryRepository).save(any(StockHistoryEntity.class));
    }

    @Test
    @DisplayName("재고 레코드가 없어도 상품이 존재하면 재고를 생성해 입고한다")
    void stockIn_createsStockWhenAbsent() {
        // given
        Long productId = 1L;
        ProductEntity product = product(productId, "후드티", 30000);
        given(stockRepository.findByProductIdWithPessimisticLock(productId)).willReturn(Optional.empty());
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(stockRepository.save(any(StockEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(stockHistoryRepository.save(any(StockHistoryEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        StockHistoryResponse response = stockService.stockIn(productId, createRequest(7, "신규 입고"));

        // then
        assertThat(response.getCurrentStocks()).isEqualTo(7); // 0 + 7
        assertThat(response.getChangedQty()).isEqualTo(7);
        verify(stockRepository).save(any(StockEntity.class));
    }

    @Test
    @DisplayName("존재하지 않는 상품을 입고하면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void stockIn_productNotFound() {
        // given
        Long productId = 999L;
        given(stockRepository.findByProductIdWithPessimisticLock(productId)).willReturn(Optional.empty());
        given(productRepository.findById(productId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockService.stockIn(productId, createRequest(10, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        verify(stockHistoryRepository, never()).save(any());
    }

    /** develop 의 ProductEntity 는 (productName, productPrice) 빌더만 있어, id 는 리플렉션으로 채운다. */
    private ProductEntity product(Long id, String name, int price) {
        ProductEntity product = ProductEntity.builder().productName(name).productPrice(price).build();
        ReflectionTestUtils.setField(product, "productId", id);
        return product;
    }

    /** StockInRequest 는 setter 가 없으므로 리플렉션으로 필드를 채운다. */
    private StockInRequest createRequest(int quantity, String reason) {
        StockInRequest request = new StockInRequest();
        ReflectionTestUtils.setField(request, "quantity", quantity);
        ReflectionTestUtils.setField(request, "reason", reason);
        return request;
    }
}
