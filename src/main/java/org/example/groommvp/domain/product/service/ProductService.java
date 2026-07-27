package org.example.groommvp.domain.product.service;

import org.example.groommvp.domain.product.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    //상품 등록
    ProductResponse createProduct(ProductCreateRequest request, MultipartFile image);

    //상품 수정
    ProductResponse updateProduct(Long productId, ProductUpdateRequest request, MultipartFile image);

    //상품 삭제
    ProductResponse deleteProduct(Long productId);

    //상품 단건 조회
    ProductDetailResponse getProduct(Long productId);

    Page<ProductListResponse> getProductList(String keyword, Pageable pageable);
}
