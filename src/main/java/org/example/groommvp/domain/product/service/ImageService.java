package org.example.groommvp.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.global.storage.S3TransactionCleanup;
import org.example.groommvp.global.storage.S3imageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final S3imageStorage s3imageStorage;
    private final S3TransactionCleanup s3TransactionCleanup;

    //이미지 등록
    @Transactional
    public ImageResponse saveImage(Long productId, MultipartFile imageFile) {
        //상품 확인
        ProductEntity product = productRepository.findByIdForUpdate(productId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        //현재 상세 이미지 개수 확인
        if (imageRepository.countByProductProductId(productId) >= 10) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }
        //s3업로드
        String objectKey = s3imageStorage.upload(imageFile, "products/" + productId + "/details");
        //메서드 실행 또는 실제 DB 커밋이 실패하면 새 S3 객체 제거
        s3TransactionCleanup.deleteAfterRollback(objectKey);

        ImageEntity image = ImageEntity.builder()
                .product(product)
                .detailImage(objectKey)
                .build();
        ImageEntity savedImage = imageRepository.save(image);

        return ImageResponse.from(savedImage, s3imageStorage.toUrl(objectKey));
    }

    //이미지 수정
    @Transactional
    public ImageResponse updateImage(Long productId, Long imageId, MultipartFile imageFile) {
        //상품에 속한 이미지인지 확인
        ImageEntity image = imageRepository.findByImageIdAndProductProductId(imageId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        //기존s3객치케보관
        String oldObjectKey = image.getDetailImage();
        //새 파일 먼저 업로드
        String newObjectKey = s3imageStorage.upload(imageFile, "products/" + productId + "/details");

        // DB가 롤백되면 새 이미지 제거
        s3TransactionCleanup.deleteAfterRollback(newObjectKey);
        //DB에는 새 객체키 저장
        image.update(newObjectKey);
        imageRepository.saveAndFlush(image);
        // DB 커밋 성공 후에만 기존 이미지 제거
        s3TransactionCleanup.deleteAfterCommit(oldObjectKey);

        //새 조회 url응답
        return ImageResponse.from(image, s3imageStorage.toUrl(newObjectKey));
    }

    //이미지 삭제
    @Transactional
    public ImageResponse deleteImage(Long productId, Long imageId) {
        //이미지 찾을 수 없을 때
        ImageEntity image = imageRepository.findByImageIdAndProductProductId(imageId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        String objectKey = image.getDetailImage();
        ImageResponse response = ImageResponse.from(image, s3imageStorage.toUrl(objectKey));
        imageRepository.delete(image);

        // DB 삭제가 실제 커밋된 뒤 S3 삭제
        s3TransactionCleanup.deleteAfterCommit(objectKey);

        return response;
    }
}
