package org.example.groommvp.domain.order.service;

import org.example.groommvp.domain.order.dto.PurchaseRequest;
import org.example.groommvp.domain.order.dto.PurchaseResponse;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.entity.OrderItem;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.entity.StockHistoryEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

    private static final String PURCHASE_REASON = "PURCHASE";

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public PurchaseService(
            ProductRepository productRepository,
            StockRepository stockRepository,
            StockHistoryRepository stockHistoryRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.stockHistoryRepository = stockHistoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public PurchaseResponse purchase(Long productId, PurchaseRequest request) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        StockEntity stock = stockRepository.findByProductIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        ProductEntity product = stock.getProduct();
        int quantity = request.quantity();

        stock.decrease(quantity);

        int orderPrice = product.getProductPrice();
        int totalPrice = orderPrice * quantity;
        Order order = orderRepository.save(new Order(totalPrice));
        orderItemRepository.save(new OrderItem(order, product, quantity, orderPrice));
        stockHistoryRepository.save(StockHistoryEntity.decrease(stock, order.getId(), quantity, PURCHASE_REASON));

        return new PurchaseResponse(
                order.getId(),
                product.getProductId(),
                quantity,
                stock.getStocks(),
                order.getCreatedAt()
        );
    }
}
