package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ProductListResponseDto;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public Page<ProductListResponseDto> getProductList(String keyword, Pageable pageable) {
        //검색어가 있으면 검색해서 페이징
        if(keyword != null && !keyword.trim().isEmpty()) {
            return productRepository.findByProductNameContaining(keyword, pageable)
                    .map(ProductListResponseDto::from);
        }

        return productRepository.findAll(pageable)
                .map(ProductListResponseDto::from);
    }

}
