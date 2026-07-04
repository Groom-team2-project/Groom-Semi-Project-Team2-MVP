package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ProductListResponse;
import org.example.groommvp.domain.product.dto.ProductResponse;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.example.groommvp.domain.product.dto.ProductCreateRequest;
import org.example.groommvp.domain.product.dto.ProductUpdateRequest;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    @Transactional
    public void createProduct(ProductCreateRequest request) {
        ProductEntity product = ProductEntity.builder()
                .productName(request.getProductName())
                .productPrice(request.getProductPrice())
                .build();
        ProductEntity savedProduct =  productRepository.save(product);

        StockEntity stock = StockEntity.builder()
                .product(savedProduct)
                .stocks(request.getStocks())
                .build();
        stockRepository.save(stock);
    }

    public ProductResponse getProduct(Long productId) {
        ProductEntity product = checkProductExists(productId);
        StockEntity stock = stockRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        return ProductResponse.from(product, stock);
    }

    @Transactional
    public void updateProduct(Long productId, ProductUpdateRequest request) {
        ProductEntity product = checkProductExists(productId);
        product.update(
                request.getProductName(),
                request.getProductPrice()
        );
    }

    @Transactional
    public void deleteProduct(Long productId) {
        ProductEntity product = checkProductExists(productId);
        productRepository.delete(product);
    }

    //제품존제를 확인해줌
    private ProductEntity checkProductExists(Long productId){
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public Page<ProductListResponse> getProductList(String keyword, Pageable pageable) {
        //검색어가 있으면 검색해서 페이징
        if(keyword != null && !keyword.trim().isEmpty()) {
            return productRepository.findByProductNameContaining(keyword, pageable)
                    .map(ProductListResponse::from);
        }

        return productRepository.findAll(pageable)
                .map(ProductListResponse::from);
    }
}
