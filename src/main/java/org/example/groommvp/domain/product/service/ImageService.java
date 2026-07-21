package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ImageCreateRequest;
import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;
    private final ProductRepository productRepository;

    //이미지 등록
    @Transactional
    public ImageResponse saveImage(Long productId, ImageCreateRequest request) {
        ProductEntity product = productRepository.findById(productId)
                .filter(foundProduct -> foundProduct.getDeletedAt() == null) //삭제 안된 상품은 거르기
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (imageRepository.existsByProductProductId(productId)) {
            throw new BusinessException(ErrorCode.IMAGE_ALREADY_EXISTS);
        }

        ImageEntity image = ImageEntity.builder()
                .product(product)
                .imageUrl(request.getImageUrl())
                .build();

        return ImageResponse.from(imageRepository.save(image));
    }

    //이미지 삭제
    @Transactional
    public void deleteImage(Long productId) {
        ImageEntity image = imageRepository.findByProductProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        imageRepository.delete(image);
    }
}
