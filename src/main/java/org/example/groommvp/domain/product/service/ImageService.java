package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ImageCreateRequest;
import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.dto.ImageUpdateRequest;
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
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (imageRepository.countByProductProductId(productId) >= 10) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        ImageEntity image = ImageEntity.builder()
                .product(product)
                .detailImage(request.getDetailImage())
                .build();

        return ImageResponse.from(imageRepository.save(image));
    }

    //이미지 수정
    @Transactional
    public ImageResponse updateImage(Long imageId, ImageUpdateRequest request) {
        //이미지 찾을 수 없을 때
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        image.update(request.getDetailImage());

        return ImageResponse.from(imageRepository.save(image));
    }

    //이미지 삭제
    @Transactional
    public ImageResponse deleteImage(Long imageId) {
        //이미지 찾을 수 없을 때
        ImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        ImageResponse response = ImageResponse.from(image);
        imageRepository.delete(image);
        return response;
    }
}
