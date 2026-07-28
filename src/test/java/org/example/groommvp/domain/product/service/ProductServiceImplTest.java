package org.example.groommvp.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.example.groommvp.domain.category.entity.CategoryEntity;
import org.example.groommvp.domain.category.repository.CategoryRepository;
import org.example.groommvp.domain.order.entity.OrderStatus;
import org.example.groommvp.domain.product.dto.ProductCreateRequest;
import org.example.groommvp.domain.product.dto.ProductDetailResponse;
import org.example.groommvp.domain.product.dto.ProductListResponse;
import org.example.groommvp.domain.product.dto.ProductResponse;
import org.example.groommvp.domain.product.dto.ProductSortType;
import org.example.groommvp.domain.product.dto.ProductUpdateRequest;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.global.storage.S3imageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock StockRepository stockRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ImageRepository imageRepository;
    @Mock S3imageStorage s3imageStorage;
    @InjectMocks ProductServiceImpl productService;

    @Test
    @DisplayName("상품 등록에 성공하면 상품과 초기 재고를 저장하고 이미지 URL을 반환한다")
    void createProduct_success() {
        CategoryEntity category = childCategory(2L);
        ProductCreateRequest request = new ProductCreateRequest("노트북", 1_500_000, 10, 2L);
        MockMultipartFile image = image();
        given(categoryRepository.findById(2L)).willReturn(Optional.of(category));
        given(s3imageStorage.upload(image, "products/main")).willReturn("products/main/new.png");
        given(productRepository.save(any(ProductEntity.class))).willAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "productId", 1L);
            return product;
        });
        given(stockRepository.save(any(StockEntity.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(s3imageStorage.toUrl("products/main/new.png")).willReturn("https://cdn/new.png");

        ProductResponse response = productService.createProduct(request, image);

        assertThat(response.getProductName()).isEqualTo("노트북");
        assertThat(response.getProductPrice()).isEqualTo(1_500_000);
        assertThat(response.getStocks()).isEqualTo(10);
        assertThat(response.getCategory()).isEqualTo(2L);
        assertThat(response.getProductImage()).isEqualTo("https://cdn/new.png");
        ArgumentCaptor<StockEntity> stockCaptor = ArgumentCaptor.forClass(StockEntity.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getProduct().getProductId()).isEqualTo(1L);
        verify(s3imageStorage, never()).delete(any());
    }

    @Test
    @DisplayName("대분류에는 상품을 등록할 수 없고 이미지를 업로드하지 않는다")
    void createProduct_rejectsRootCategory() {
        ProductCreateRequest request = new ProductCreateRequest("노트북", 1000, 1, 1L);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(rootCategory(1L)));

        assertError(() -> productService.createProduct(request, image()), ErrorCode.INVALID_PRODUCT_CATEGORY);

        verify(s3imageStorage, never()).upload(any(), any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미지 업로드 후 상품 저장이 실패하면 업로드한 이미지를 삭제한다")
    void createProduct_deletesUploadedImageWhenSaveFails() {
        ProductCreateRequest request = new ProductCreateRequest("노트북", 1000, 1, 2L);
        given(categoryRepository.findById(2L)).willReturn(Optional.of(childCategory(2L)));
        given(s3imageStorage.upload(any(), any())).willReturn("products/main/new.png");
        given(productRepository.save(any())).willThrow(new IllegalStateException("db error"));

        assertThatThrownBy(() -> productService.createProduct(request, image()))
                .isInstanceOf(IllegalStateException.class);

        verify(s3imageStorage).delete("products/main/new.png");
        verify(stockRepository, never()).save(any());
    }

    @Test
    @DisplayName("새 이미지로 상품을 수정하면 저장 후 기존 이미지를 삭제한다")
    void updateProduct_withNewImage() {
        ProductEntity product = product(1L, "이전 상품", 1000, "old.png", childCategory(2L));
        StockEntity stock = StockEntity.builder().product(product).stocks(3).build();
        CategoryEntity newCategory = childCategory(3L);
        MockMultipartFile newImage = image();
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(categoryRepository.findById(3L)).willReturn(Optional.of(newCategory));
        given(stockRepository.findByProduct_ProductId(1L)).willReturn(Optional.of(stock));
        given(s3imageStorage.upload(newImage, "products/main")).willReturn("new.png");
        given(s3imageStorage.toUrl("new.png")).willReturn("https://cdn/new.png");

        ProductResponse response = productService.updateProduct(
                1L, new ProductUpdateRequest("새 상품", 2000, 3L), newImage);

        assertThat(product.getProductName()).isEqualTo("새 상품");
        assertThat(product.getProductPrice()).isEqualTo(2000);
        assertThat(product.getProductImage()).isEqualTo("new.png");
        assertThat(product.getCategory()).isSameAs(newCategory);
        assertThat(response.getProductImage()).isEqualTo("https://cdn/new.png");
        InOrder order = inOrder(productRepository, s3imageStorage);
        order.verify(productRepository).saveAndFlush(product);
        order.verify(s3imageStorage).delete("old.png");
    }

    @Test
    @DisplayName("이미지 없이 상품을 수정하면 기존 이미지를 유지한다")
    void updateProduct_withoutImage() {
        ProductEntity product = product(1L, "이전 상품", 1000, "old.png", childCategory(2L));
        StockEntity stock = StockEntity.builder().product(product).stocks(3).build();
        CategoryEntity category = childCategory(3L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(categoryRepository.findById(3L)).willReturn(Optional.of(category));
        given(stockRepository.findByProduct_ProductId(1L)).willReturn(Optional.of(stock));
        given(s3imageStorage.toUrl("old.png")).willReturn("https://cdn/old.png");

        ProductResponse response = productService.updateProduct(
                1L, new ProductUpdateRequest("새 상품", 2000, 3L), null);

        assertThat(product.getProductImage()).isEqualTo("old.png");
        assertThat(response.getProductImage()).isEqualTo("https://cdn/old.png");
        verify(s3imageStorage, never()).upload(any(), any());
        verify(s3imageStorage, never()).delete(any());
    }

    @Test
    @DisplayName("새 이미지 업로드 후 수정 저장이 실패하면 새 이미지만 삭제한다")
    void updateProduct_deletesNewImageWhenSaveFails() {
        ProductEntity product = product(1L, "이전 상품", 1000, "old.png", childCategory(2L));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(categoryRepository.findById(3L)).willReturn(Optional.of(childCategory(3L)));
        given(stockRepository.findByProduct_ProductId(1L))
                .willReturn(Optional.of(StockEntity.builder().product(product).stocks(0).build()));
        given(s3imageStorage.upload(any(), any())).willReturn("new.png");
        given(productRepository.saveAndFlush(product)).willThrow(new IllegalStateException("db error"));

        assertThatThrownBy(() -> productService.updateProduct(
                1L, new ProductUpdateRequest("새 상품", 2000, 3L), image()))
                .isInstanceOf(IllegalStateException.class);

        verify(s3imageStorage).delete("new.png");
        verify(s3imageStorage, never()).delete("old.png");
    }

    @Test
    @DisplayName("재고가 없을 때만 상품을 소프트 삭제한다")
    void deleteProduct_success() {
        ProductEntity product = product(1L, "상품", 1000, "main.png", childCategory(2L));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(stockRepository.findByProduct_ProductId(1L))
                .willReturn(Optional.of(StockEntity.builder().product(product).stocks(0).build()));

        ProductResponse response = productService.deleteProduct(1L);

        assertThat(product.getDeletedAt()).isNotNull();
        assertThat(response.getProductName()).isEqualTo("상품");
    }

    @Test
    @DisplayName("재고가 남은 상품은 삭제할 수 없다")
    void deleteProduct_rejectsRemainingStock() {
        ProductEntity product = product(1L, "상품", 1000, "main.png", childCategory(2L));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(stockRepository.findByProduct_ProductId(1L))
                .willReturn(Optional.of(StockEntity.builder().product(product).stocks(1).build()));

        assertError(() -> productService.deleteProduct(1L), ErrorCode.PRODUCT_STOCK_REMAINING);
        assertThat(product.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("이미 삭제된 상품은 조회할 수 없다")
    void getProduct_rejectsDeletedProduct() {
        ProductEntity product = product(1L, "상품", 1000, "main.png", childCategory(2L));
        product.delete();
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        assertError(() -> productService.getProduct(1L), ErrorCode.PRODUCT_ALREADY_DELETED);
        verify(stockRepository, never()).findByProduct_ProductId(any());
    }

    @Test
    @DisplayName("상품 상세 조회 시 대표 이미지와 상세 이미지 URL을 변환해 반환한다")
    void getProduct_success() {
        ProductEntity product = product(1L, "상품", 1000, "main.png", childCategory(2L));
        StockEntity stock = StockEntity.builder().product(product).stocks(5).build();
        ImageEntity detailImage = ImageEntity.builder().product(product).detailImage("detail.png").build();
        ReflectionTestUtils.setField(detailImage, "imageId", 10L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(stockRepository.findByProduct_ProductId(1L)).willReturn(Optional.of(stock));
        given(imageRepository.findAllByProductProductIdOrderByImageIdAsc(1L))
                .willReturn(List.of(detailImage));
        given(s3imageStorage.toUrl("main.png")).willReturn("https://cdn/main.png");
        given(s3imageStorage.toUrl("detail.png")).willReturn("https://cdn/detail.png");

        ProductDetailResponse response = productService.getProduct(1L);

        assertThat(response.getStocks()).isEqualTo(5);
        assertThat(response.getProductImage()).isEqualTo("https://cdn/main.png");
        assertThat(response.getDetailImages()).singleElement().satisfies(detail -> {
            assertThat(detail.getImageId()).isEqualTo(10L);
            assertThat(detail.getDetailImage()).isEqualTo("https://cdn/detail.png");
        });
    }

    @Test
    @DisplayName("상품이 없으면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void getProduct_notFound() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());
        assertError(() -> productService.getProduct(999L), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("일반 목록 조회는 검색어를 정규화하고 기본 최신순과 재고를 적용한다")
    void getProductList_latestAndStockMapping() {
        ProductEntity first = product(1L, "상품1", 1000, "1.png", childCategory(2L));
        ProductEntity second = product(2L, "상품2", 2000, "2.png", childCategory(2L));
        given(productRepository.findAllByKeywordAndCategory(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(first, second)));
        given(stockRepository.findAllByProduct_ProductIdIn(List.of(1L, 2L)))
                .willReturn(List.of(StockEntity.builder().product(first).stocks(7).build()));

        Page<ProductListResponse> response =
                productService.getProductList("  상품  ", 2L, null, 0, 20);

        assertThat(response.getContent()).extracting(ProductListResponse::getStocks)
                .containsExactly(7, 0);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllByKeywordAndCategory(
                org.mockito.ArgumentMatchers.eq("상품"),
                org.mockito.ArgumentMatchers.eq(2L),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection().isDescending()).isTrue();
    }

    @Test
    @DisplayName("인기순 목록은 결제 완료 주문 집계 쿼리를 사용한다")
    void getProductList_popular() {
        given(productRepository.findAllOrderByCompletedOrderCountDesc(
                any(), any(), any(), any())).willReturn(Page.empty());
        given(stockRepository.findAllByProduct_ProductIdIn(List.of())).willReturn(List.of());

        productService.getProductList("   ", null, ProductSortType.POPULAR, 1, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllOrderByCompletedOrderCountDesc(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(OrderStatus.COMPLETED),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
        verify(productRepository, never()).findAllByKeywordAndCategory(any(), any(), any());
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode errorCode) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("productImage", "product.png", "image/png", new byte[] {1});
    }

    private CategoryEntity rootCategory(Long id) {
        CategoryEntity category = CategoryEntity.builder().categoryName("대분류").build();
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }

    private CategoryEntity childCategory(Long id) {
        CategoryEntity category = CategoryEntity.builder()
                .categoryName("중분류")
                .parentCategory(rootCategory(100L))
                .build();
        ReflectionTestUtils.setField(category, "categoryId", id);
        return category;
    }

    private ProductEntity product(
            Long id, String name, int price, String imageKey, CategoryEntity category) {
        ProductEntity product = ProductEntity.builder()
                .productName(name)
                .productPrice(price)
                .productImage(imageKey)
                .category(category)
                .build();
        ReflectionTestUtils.setField(product, "productId", id);
        return product;
    }
}
