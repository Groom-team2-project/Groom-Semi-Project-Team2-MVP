package org.example.groommvp.domain.cancel.service;

import java.util.ArrayList;
import java.util.List;

import org.example.groommvp.domain.cancel.dto.OrderCancelResponse;
import org.example.groommvp.domain.cancel.dto.RestoredItemResponse;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class OrderCancelService {

    private static final String CANCEL_REASON = "ORDER_CANCEL";
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    public OrderCancelService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            StockRepository stockRepository,
            StockHistoryRepository stockHistoryRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.stockRepository = stockRepository;
        this.stockHistoryRepository = stockHistoryRepository;
    }

    @Transactional
    public OrderCancelResponse cancel(Long orderId) {

        // 1. 주문 조회
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 2. 취소 — 재고 처리 방식은 "취소 전" 상태로 갈린다.
        //    결제 대기(예약만 잡힌) 주문은 실재고가 줄지 않았으므로 예약만 해제하고,
        //    결제 완료 주문은 실재고가 이미 차감됐으므로 실재고를 복구한다.
        boolean reservedOnly = order.getStatus().isPendingPayment();
        order.cancel();

        // 3. 이 주문에 속한 품목들 조회
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);


        List<RestoredItemResponse> restoredItems = new ArrayList<>();
        for (OrderItem orderItem : orderItems) {
            Long productId = orderItem.getProduct().getProductId();
            int quantity = orderItem.getQuantity();

            StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

            if (reservedOnly) {
                stock.release(quantity);
                stockHistoryRepository.save(
                    StockHistoryEntity.release(stock, orderId, quantity, CANCEL_REASON)
                );
            } else {
                stock.increase(quantity);
                stockHistoryRepository.save(
                    StockHistoryEntity.restore(stock, orderId, quantity, CANCEL_REASON)
                );
            }

            restoredItems.add(new RestoredItemResponse(productId, quantity));
        }

        return new OrderCancelResponse(
            order.getId(),
            order.getStatus(),
            order.getCanceledAt(),
            restoredItems
        );
    }
}
