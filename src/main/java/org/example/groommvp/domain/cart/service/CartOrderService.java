package org.example.groommvp.domain.cart.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.example.groommvp.domain.cart.config.CartCacheNames;
import org.example.groommvp.domain.cart.scheduler.ReservationExpiryScheduler;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니를 하나의 주문으로 전환하는 서비스. (E→C→D 구매 흐름의 시작점)
 *
 * <p>재고/주문 도메인(파트 C)의 <b>공개 빌딩블록</b>({@link StockEntity#reserve},
 * {@link Order}, {@link OrderItem}, {@link StockHistoryEntity})만 사용해 조립하며,
 * 해당 도메인 코드를 수정하지 않는다. 재고 예약은 기존 단건 구매({@code PurchaseService})와
 * 동일하게 상품별 비관적 락으로 처리한다.
 *
 * <p><b>인터페이스 합의 필요:</b> 기획서 §8에 따라 E→C→D 흐름은 파트 C/D 와 사전 합의가
 * 전제다. 현재 구현은 결제 전에는 재고를 즉시 차감하지 않고 예약한 뒤, 결제 성공/실패 흐름에서
 * 확정 또는 해제되도록 연결하는 구조다.
 */
@Service
public class CartOrderService {

    private static final String CART_ORDER_REASON = "CART_ORDER";

    private final CartRepository cartRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 결제 마감까지 주는 시간. 이 시각이 지나면 예약 재고가 회수된다.
     *
     * <p><b>회수 스케줄러와 같은 설정값을 쓴다.</b> 주문에 적히는 마감 시각과 실제로 회수가
     * 일어나는 시점이 서로 다른 상수에서 나오면, 한쪽만 바꿨을 때 "화면에는 아직 5분 남았는데
     * 이미 회수된" 주문이 생긴다. ({@link ReservationExpiryScheduler} 가 같은 키를 읽는다)
     */
    private final Duration paymentTimeout;

    public CartOrderService(
            CartRepository cartRepository,
            StockRepository stockRepository,
            StockHistoryRepository stockHistoryRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            @Value("${cart.reservation.expiry.timeout:PT30M}") Duration paymentTimeout) {
        this.cartRepository = cartRepository;
        this.stockRepository = stockRepository;
        this.stockHistoryRepository = stockHistoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentTimeout = paymentTimeout;
    }

    /** 장바구니 전체를 주문으로 전환하고 장바구니를 비운다. */
    @CacheEvict(cacheNames = CartCacheNames.CART, key = "#memberId")
    @Transactional
    public CartCheckoutResponse checkout(Long memberId) {
        CartEntity cart = cartRepository.findByMemberIdWithItems(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        if (cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        // 데드락 방지: 재고 락을 항상 상품 ID 오름차순으로 잡는다. 담은 순서대로 잠그면
        // 같은 두 상품을 반대 순서로 담은 회원끼리 서로의 락을 기다려 교착한다.
        List<CartItemEntity> items = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getProductId()))
                .toList();

        // 1) 재고 예약 + 총액 계산 (상품별 비관적 락)
        long totalPrice = 0L;
        List<Line> lines = new ArrayList<>();
        for (CartItemEntity item : items) {
            ProductEntity product = item.getProduct();
            int quantity = item.getQuantity();

            // 담은 뒤 상품이 삭제됐을 수 있다. 담기({@code CartService#addItem})에서만 막으면
            // 장바구니에 오래 머문 항목이 그대로 주문된다.
            if (product.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            StockEntity stock = stockRepository
                    .findByProductIdWithPessimisticLock(product.getProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
            stock.reserve(quantity);

            int orderPrice = product.getProductPrice();
            totalPrice += (long) orderPrice * quantity;
            lines.add(new Line(stock, product, quantity, orderPrice));
        }

        // 2) 단일 주문 생성 후 주문 항목/재고 이력 적재 (주문자 = 장바구니 소유 회원)
        //    결제 마감 시각을 함께 박는다. 비워두면 단건 구매(PurchaseService)와 달리 장바구니
        //    주문만 마감이 null 이라, 응답을 받는 쪽이 "마감 없는 주문"으로 오해한다.
        Order order = orderRepository.save(
                Order.pendingPayment(memberId, totalPrice, LocalDateTime.now().plus(paymentTimeout)));
        List<CartCheckoutResponse.OrderedItem> orderedItems = new ArrayList<>();
        for (Line line : lines) {
            orderItemRepository.save(
                    new OrderItem(order, line.product, line.quantity, line.orderPrice));
            stockHistoryRepository.save(
                    StockHistoryEntity.reserve(line.stock, order.getId(), line.quantity, CART_ORDER_REASON));
            orderedItems.add(new CartCheckoutResponse.OrderedItem(
                    line.product.getProductId(),
                    line.quantity,
                    line.orderPrice,
                    line.stock.getAvailableStocks()));
        }

        // 3) 주문 전환 완료 → 장바구니 비우기
        cart.clear();

        return new CartCheckoutResponse(order.getId(), orderedItems, totalPrice, order.getCreatedAt());
    }

    private record Line(StockEntity stock, ProductEntity product, int quantity, int orderPrice) {
    }
}
