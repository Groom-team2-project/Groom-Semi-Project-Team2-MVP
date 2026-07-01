package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ProductCreateRequest;
import org.example.groommvp.domain.product.dto.ProductUpdateRequest;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public void createProduct(ProductCreateRequest request) {
        ProductEntity product = ProductEntity.builder()
                .productName(request.getProductName())
                .productPrice(request.getProductPrice())
                .build();
        productRepository.save(product);
    }

    @Transactional
    public void updateProduct(Long productId, ProductUpdateRequest request) {
        ProductEntity product = checkProductExists(productId);
        product.update(
                request.getProductName(),
                request.getProductPrice()
        );
    }

    private ProductEntity checkProductExists(Long productId){
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("해당 제품 없음"));
    }
}
