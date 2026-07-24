package org.example.groommvp.domain.product.service;

import org.example.groommvp.domain.product.dto.ProductCreateRequest;
import org.example.groommvp.domain.product.dto.ProductListResponse;
import org.example.groommvp.domain.product.dto.ProductResponse;
import org.example.groommvp.domain.product.dto.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    //상품 등록
    ProductResponse createProduct(ProductCreateRequest request);

    //상품 수정
    ProductResponse updateProduct(Long productId, ProductUpdateRequest request);

    //상품 삭제
    ProductResponse deleteProduct(Long productId);

    //상품 단건 조회
    ProductResponse getProduct(Long productId);

    Page<ProductListResponse> getProductList(String keyword, Pageable pageable);
}
