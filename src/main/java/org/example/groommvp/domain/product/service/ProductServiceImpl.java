package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.category.dto.CategoryResponse;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
import org.example.groommvp.domain.product.dto.*;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService{

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final CategoryRepository categoryRepository;
    private final ImageRepository imageRepository;

    //상품 등록
    @Override
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        //카테고리 확인
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.getParentCategory() == null) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_CATEGORY);
        }

        ProductEntity product = ProductEntity.builder()
                .productName(request.getProductName())
                .productPrice(request.getProductPrice())
                .productImage(request.getProductImage())
                .category(category)
                .build();
        ProductEntity savedProduct =  productRepository.save(product);

        StockEntity stock = StockEntity.builder()
                .product(savedProduct)
                .stocks(request.getStocks())
                .build();
        stockRepository.save(stock);

        return ProductResponse.from(product, stock);
    }

    //상품 수정
    @Override
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {

        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)); //등록되지 않은 상품
        if (product.getDeletedAt() != null) { //이미 삭제된 상품
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_DELETED);
        }

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.getParentCategory() == null) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_CATEGORY);
        }

        StockEntity stock = stockRepository.findByProduct_ProductId(productId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        product.update(
                request.getProductName(),
                request.getProductPrice(),
                request.getImageUrl(),
                category
        );

        return ProductResponse.from(product, stock);
    }

    //상품 삭제
    @Override
    @Transactional
    public ProductResponse deleteProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)); //등록되지 않은 상품
        if (product.getDeletedAt() != null) { //이미 삭제된 상품
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_DELETED);
        }

        StockEntity stock = stockRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        if (stock.getStocks() > 0) {
            throw new BusinessException(ErrorCode.PRODUCT_STOCK_REMAINING);
        }
        ProductResponse response = ProductResponse.from(product, stock);
        productRepository.delete(product);

        return response;
    }

    //상품 단건 조회
    @Override
    public ProductDetailResponse getProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)); //등록되지 않은 상품
        if (product.getDeletedAt() != null) { //이미 삭제된 상품
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_DELETED);
        }
        StockEntity stock = stockRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        List<ImageEntity> images = imageRepository
                .findAllByProductProductIdOrderByImageIdAsc(productId);

        return ProductDetailResponse.from(product, stock, images);
    }

    @Override
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
