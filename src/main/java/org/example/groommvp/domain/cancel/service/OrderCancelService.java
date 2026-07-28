package org.example.groommvp.domain.cancel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

import org.example.groommvp.domain.cancel.dto.OrderCancelResponse;
import org.example.groommvp.domain.cancel.dto.RestoredItemResponse;
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
    public OrderCancelResponse cancel(Long orderId, Long memberId) {

        // 1. 주문 조회
        Order order = orderRepository.findByIdWithPessimisticLock(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (memberId == null
                || order.getMemberId() == null
                || !order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN);
        }

        // 2. 결제 완료 주문은 이 API로 취소할 수 없다.
        //    여기서 취소하면 재고는 복구되지만 토스 결제가 취소되지 않아 결제 금액이 환불되지 않는다.
        //    결제 취소까지 수행하는 환불 API(POST /orders/{orderId}/payments/refund)로만 처리한다.
        if (order.getStatus().isCompleted()) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_REQUIRED);
        }

        OrderStatus previousStatus = order.getStatus(); // 취소하기 전 상태 기억

        // 2. 취소
        order.cancel();

        // 3. 이 주문에 속한 품목들 조회
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order).stream()
                .sorted(Comparator.comparing(
                        item -> item.getProduct().getProductId()
                ))
                .toList();

        List<RestoredItemResponse> restoredItems = new ArrayList<>();
        for (OrderItem orderItem : orderItems) {
            Long productId = orderItem.getProduct().getProductId();
            int quantity = orderItem.getQuantity();

            StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

            if (previousStatus == OrderStatus.PENDING_PAYMENT) {
                stock.release(quantity);
                stockHistoryRepository.save(
                        StockHistoryEntity.release(
                                stock,
                                orderId,
                                quantity,
                                CANCEL_REASON
                        )
                );
            } else if (previousStatus == OrderStatus.COMPLETED) {
                stock.increase(quantity);
                stockHistoryRepository.save(
                        StockHistoryEntity.restore(
                                stock,
                                orderId,
                                quantity,
                                CANCEL_REASON
                        )
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
