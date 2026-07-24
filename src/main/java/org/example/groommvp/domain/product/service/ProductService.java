package org.example.groommvp.domain.product.service;

import org.example.groommvp.domain.product.dto.ProductCreateRequest;
import org.example.groommvp.domain.product.dto.ProductListResponse;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
import org.example.groommvp.domain.product.dto.ProductResponse;
import org.example.groommvp.domain.product.dto.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

<<<<<<< HEAD
public interface ProductService {

    //상품 등록
    ProductResponse createProduct(ProductCreateRequest request);
=======
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final CategoryRepository categoryRepository;

    //상품 등록
    @Transactional
    public Long createProduct(ProductCreateRequest request) {
        CategoryEntity category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            if (category.getParentCategory() == null) {
                throw new BusinessException(ErrorCode.INVALID_PARENT_CATEGORY);
            }
        }

        ProductEntity product = ProductEntity.builder()
                .productName(request.getProductName())
                .productPrice(request.getProductPrice())
                .category(category)
                .build();
        ProductEntity savedProduct =  productRepository.save(product);

        StockEntity stock = StockEntity.builder()
                .product(savedProduct)
                .stocks(request.getStocks())
                .build();
        stockRepository.save(stock);

        return savedProduct.getProductId();
    }

    //상품 단건 조회
    public ProductResponse getProduct(Long productId) {
        ProductEntity product = getActiveProduct(productId);
        StockEntity stock = stockRepository.findByProduct_ProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        return ProductResponse.from(product, stock);
    }
>>>>>>> 015504d4c4f510512b515b9c7b5e6a7239c59be7

    //상품 수정
    ProductResponse updateProduct(Long productId, ProductUpdateRequest request);

    //상품 삭제
    ProductResponse deleteProduct(Long productId);

    //상품 단건 조회
    ProductResponse getProduct(Long productId);

    public Page<ProductListResponse> getProductList(String keyword, Pageable pageable);
}
