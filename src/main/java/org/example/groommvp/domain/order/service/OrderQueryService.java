package org.example.groommvp.domain.order.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.order.dto.OrderResponse;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 회원의 주문 내역을 최신순으로 조회한다. (마이페이지)
     *
     * <p>주문 항목은 주문 ID 목록으로 <b>한 번에</b> 가져와 주문 수만큼 쿼리가 나가는 N+1 을 피한다.
     */
    public List<OrderResponse> getMyOrders(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<Order> orders = orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        if (orders.isEmpty()) {
            return List.of();
        }

        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository
                .findByOrderIdsWithProduct(orders.stream().map(Order::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(orderItem -> orderItem.getOrder().getId()));

        return orders.stream()
                .map(order -> OrderResponse.from(
                        order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    public OrderResponse getOrder(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (memberId == null || order.getMemberId() == null || !order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderIdWithProduct(orderId);

        return OrderResponse.from(order, orderItems);
    }
}
