package org.example.groommvp.domain.cancel.service;

import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderCancelService {
    
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

    // @Transactional
    // public OrderCancelResponse cancel(Long orderId) {

    //     // 1. 주문 조회
    //     Order order = orderRepository.findById(orderId)
    //         .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    //     // 2. 취소
    //     order.cancel();

    //     // 3. 이 주문에 속한 품목들 조회
    //     List<OrderItem> orderItems = orderItemRepository.findByOrder(order);

    //     for (OrderItem orderItem : orderItems) {

    //     }
    // }
}
