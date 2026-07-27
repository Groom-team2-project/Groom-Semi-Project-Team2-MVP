package org.example.groommvp.domain.cart.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제되지 않고 방치된 주문의 <b>예약 재고를 회수</b>하는 서비스.
 *
 * <p><b>왜 필요한가:</b> {@link CartOrderService#checkout} 은 결제 전에 재고를 차감하지 않고
 * {@link StockEntity#reserve 예약}만 한다. 결제가 성공하면 확정되고 실패하면 해제되는 구조인데,
 * 사용자가 결제창을 그냥 닫아버리면 어느 쪽도 일어나지 않는다. 그러면 그 예약은 <b>영원히</b>
 * 남아 가용 재고를 잠식한다. 판매 가능한 물건이 "품절"로 보이게 되는 재고 누수다.
 *
 * <p>재고/주문 도메인(파트 C)의 <b>공개 빌딩블록</b>({@link StockEntity#release},
 * {@link StockHistoryEntity#release}, {@link Order#cancel})만 사용해 조립한다. 두 메서드 모두
 * "결제 실패 또는 시간 초과로 예약을 푼다"는 용도로 이미 만들어져 있었으나 호출자가 없었다.
 *
 * <p><b>동시성:</b> 만료 처리와 결제 완료가 겹칠 수 있다. 주문 행을 잠근 뒤 상태를 <b>다시</b>
 * 확인해, 그사이 결제가 끝난 주문은 건드리지 않는다. 재고는 {@code checkout} 과 같은 이유로
 * 상품 ID 오름차순으로 잠가 교착을 피한다.
 *
 * <p><b>주문 단위 트랜잭션:</b> 한 주문의 실패가 나머지 회수를 막지 않도록 주문마다 독립
 * 트랜잭션으로 처리한다. (스케줄러가 비트랜잭션 상태에서 건별로 호출한다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExpiryService {

    private static final String EXPIRE_REASON = "RESERVATION_EXPIRED";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    /**
     * 회수 대상 주문 ID를 오래된 순으로 찾는다.
     *
     * @param threshold 이 시각 이전에 생성된 미결제 주문이 대상
     * @param batchSize 한 번에 가져올 최대 건수
     */
    @Transactional(readOnly = true)
    public List<Long> findExpiredOrderIds(LocalDateTime threshold, int batchSize) {
        return orderRepository.findIdsByStatusCreatedBefore(
                OrderStatus.PENDING_PAYMENT, threshold, PageRequest.of(0, batchSize));
    }

    /**
     * 주문 하나의 예약 재고를 회수하고 주문을 취소 상태로 만든다.
     *
     * @return 실제로 회수했으면 true, 그사이 결제/취소되어 건드리지 않았으면 false
     */
    @Transactional
    public boolean releaseReservation(Long orderId) {
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 락을 잡는 사이에 결제가 끝났거나 이미 취소됐을 수 있다. 그런 주문은 대상이 아니다.
        if (!order.getStatus().isPendingPayment()) {
            return false;
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder(order).stream()
                .sorted(java.util.Comparator.comparing(item -> item.getProduct().getProductId()))
                .toList();

        for (OrderItem orderItem : orderItems) {
            Long productId = orderItem.getProduct().getProductId();
            int quantity = orderItem.getQuantity();

            StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

            // 예약분만 되돌린다. increase() 를 쓰면 실물 재고가 늘어나 재고가 부풀려진다.
            stock.release(quantity);
            stockHistoryRepository.save(
                    StockHistoryEntity.release(stock, orderId, quantity, EXPIRE_REASON));
        }

        order.cancel();
        log.info("미결제 주문의 예약 재고를 회수했습니다. orderId={}, items={}", orderId, orderItems.size());
        return true;
    }
}
