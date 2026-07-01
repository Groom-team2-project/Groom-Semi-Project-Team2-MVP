package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ProductListResponse;
import org.example.groommvp.domain.product.repository.ProductRepository;
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
