package org.example.groommvp.domain.cart.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.cart.config.CartCacheNames;
import org.example.groommvp.domain.cart.dto.CartCheckoutResponse;
import org.example.groommvp.domain.cart.entity.CartEntity;
import org.example.groommvp.domain.cart.entity.CartItemEntity;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니를 하나의 주문으로 전환하는 서비스. (E→C→D 구매 흐름의 시작점)
 *
 * <p>재고/주문 도메인(파트 C)의 <b>공개 빌딩블록</b>({@link StockEntity#decrease},
 * {@link Order}, {@link OrderItem}, {@link StockHistoryEntity})만 사용해 조립하며,
 * 해당 도메인 코드를 수정하지 않는다. 재고 차감은 기존 단건 구매({@code PurchaseService})와
 * 동일하게 상품별 비관적 락으로 처리한다.
 *
 * <p><b>인터페이스 합의 필요:</b> 기획서 §8에 따라 E→C→D 흐름은 파트 C/D 와 사전 합의가
 * 전제다. 현재 구현은 재고 "예약(reserved)" 도입 전의 즉시 차감 방식으로, 파트 C 의 예약
 * 모델이 확정되면 그에 맞춰 교체한다.
 */
@Service
@RequiredArgsConstructor
public class CartOrderService {

    private static final String CART_ORDER_REASON = "CART_ORDER";

    private final CartRepository cartRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /** 장바구니 전체를 주문으로 전환하고 장바구니를 비운다. */
    @CacheEvict(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional
    public CartCheckoutResponse checkout(Long memberId) {
        CartEntity cart = cartRepository.findByMemberIdWithItems(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        List<CartItemEntity> items = cart.getItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        // 1) 재고 차감 + 총액 계산 (상품별 비관적 락)
        long totalPrice = 0L;
        List<Line> lines = new ArrayList<>();
        for (CartItemEntity item : items) {
            ProductEntity product = item.getProduct();
            int quantity = item.getQuantity();

            StockEntity stock = stockRepository
                    .findByProductIdWithPessimisticLock(product.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
            stock.decrease(quantity);

            int orderPrice = product.getProductPrice();
            totalPrice += (long) orderPrice * quantity;
            lines.add(new Line(stock, product, quantity, orderPrice));
        }

        // 2) 단일 주문 생성 후 주문 항목/재고 이력 적재
        Order order = orderRepository.save(new Order(totalPrice));
        List<CartCheckoutResponse.OrderedItem> orderedItems = new ArrayList<>();
        for (Line line : lines) {
            orderItemRepository.save(
                    new OrderItem(order, line.product, line.quantity, line.orderPrice));
            stockHistoryRepository.save(
                    StockHistoryEntity.decrease(line.stock, order.getId(), line.quantity, CART_ORDER_REASON));
            orderedItems.add(new CartCheckoutResponse.OrderedItem(
                    line.product.getProductId(),
                    line.quantity,
                    line.orderPrice,
                    line.stock.getStocks()));
        }

        // 3) 주문 전환 완료 → 장바구니 비우기
        cart.clear();

        return new CartCheckoutResponse(order.getId(), orderedItems, totalPrice, order.getCreatedAt());
    }

    private record Line(StockEntity stock, ProductEntity product, int quantity, int orderPrice) {
    }
}
