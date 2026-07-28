package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.product.dto.*;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    @Transactional
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

        productRepository.incrementViewCount(productId); //조회수 추가

        return ProductDetailResponse.from(product, stock, images);
    }

    //상품 목록 조회 (검색 + 카테고리필터 + 정렬 + 재고 일괄조회)
    @Override
    public Page<ProductListResponse> getProductList(
            String keyword, Long categoryId, ProductSortType sortType, int page, int size
    ) {
        ProductSortType sort = sortType != null ? sortType : ProductSortType.LATEST;
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<ProductEntity> productPage;

        if (sort == ProductSortType.POPULAR) {
            // 인기순은 결제완료 주문건수 집계가 필요해서 별도 쿼리로 처리
            Pageable pageable = PageRequest.of(page, size); // Sort 없이! 여기 JPQL의 order by가 정렬 담당
            productPage = productRepository.findAllOrderByCompletedOrderCountDesc(
                    normalizedKeyword, categoryId, OrderStatus.COMPLETED, pageable);
        } else {
            // 최신순/조회수순/가격순은 컬럼 하나 기준 정렬이라 Sort로 처리
            Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
            productPage = productRepository.findAllByKeywordAndCategory(
                    normalizedKeyword, categoryId, pageable);
        }

        // 재고는 상품마다 따로 조회하지 않고, 이 페이지에 나온 상품들의 재고를 한 번에 묶어서 조회
        List<Long> productIds = productPage.getContent().stream()
                .map(ProductEntity::getProductId)
                .toList();

        Map<Long, Integer> stocksByProductId = stockRepository.findAllByProduct_ProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        s -> s.getProduct().getProductId(),
                        StockEntity::getStocks
                ));

        return productPage.map(product ->
                ProductListResponse.from(product, stocksByProductId.get(product.getProductId())));
    }

    private Sort resolveSort(ProductSortType sortType) {
        return switch (sortType) {
            case VIEW_COUNT -> Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "productId"));
            case PRICE_ASC ->
                    Sort.by(Sort.Direction.ASC, "productPrice").and(Sort.by(Sort.Direction.DESC, "productId"));
            case PRICE_DESC ->
                    Sort.by(Sort.Direction.DESC, "productPrice").and(Sort.by(Sort.Direction.DESC, "productId"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "productId"));
        };
    }
}
