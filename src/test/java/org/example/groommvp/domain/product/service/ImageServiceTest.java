package org.example.groommvp.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.groommvp.domain.product.dto.ImageCreateRequest;
import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ImageService imageService;

    @Test
    void 상품에_이미지_한_장을_등록한다() {
        Long productId = 1L;
        ProductEntity product = ProductEntity.builder()
                .productName("상품")
                .productPrice(1000)
                .build();
        ImageCreateRequest request = new ImageCreateRequest("https://example.com/image.jpg");

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(imageRepository.existsByProductProductId(productId)).willReturn(false);
        given(imageRepository.save(org.mockito.ArgumentMatchers.any(ImageEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ImageResponse response = imageService.saveImage(productId, request);

        assertThat(response.getImageUrl()).isEqualTo(request.getImageUrl());
        verify(imageRepository).save(org.mockito.ArgumentMatchers.any(ImageEntity.class));
    }

    @Test
    void 이미지가_이미_있으면_추가로_등록할_수_없다() {
        Long productId = 1L;
        ProductEntity product = ProductEntity.builder()
                .productName("상품")
                .productPrice(1000)
                .build();

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(imageRepository.existsByProductProductId(productId)).willReturn(true);

        assertThatThrownBy(() -> imageService.saveImage(
                productId,
                new ImageCreateRequest("https://example.com/second.jpg")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_ALREADY_EXISTS);

        verify(imageRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 상품의_이미지를_삭제한다() {
        Long productId = 1L;
        ImageEntity image = ImageEntity.builder()
                .product(ProductEntity.builder().productName("상품").productPrice(1000).build())
                .imageUrl("https://example.com/image.jpg")
                .build();

        given(imageRepository.findByProductProductId(productId)).willReturn(Optional.of(image));

        imageService.deleteImage(productId);

        verify(imageRepository).delete(image);
    }
}
