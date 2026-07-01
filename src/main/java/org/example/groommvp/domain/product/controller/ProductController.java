package org.example.groommvp.domain.product.controller;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.PageResponse;
import org.example.groommvp.domain.product.dto.ProductListResponse;
import org.example.groommvp.domain.product.service.ProductService;
import org.example.groommvp.global.common.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.example.groommvp.domain.product.dto.ProductCreateRequest;
import org.example.groommvp.domain.product.dto.ProductUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<Void> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> updateProduct(@PathVariable Long productId,
                                              @Valid @RequestBody ProductUpdateRequest request) {
        productService.updateProduct(productId, request);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
  
      // 검색
    @GetMapping("/products")
    public ApiResponse<PageResponse<ProductListResponse>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ){
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductListResponse> productPage = productService.getProductList(keyword, pageable);

        PageResponse<ProductListResponse> pageData = PageResponse.from(productPage);

        ApiResponse<PageResponse<ProductListResponse>> response = new ApiResponse<>();
        response.setStatus("SUCCESS");
        response.setData(pageData);
        response.setMessage(null);

        return response;
    }
}
