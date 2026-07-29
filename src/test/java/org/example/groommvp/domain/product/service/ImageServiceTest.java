package org.example.groommvp.domain.product.service;

import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.entity.ImageEntity;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ImageRepository;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.example.groommvp.global.storage.S3TransactionCleanup;
import org.example.groommvp.global.storage.S3imageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long IMAGE_ID = 10L;
    private static final String OLD_OBJECT_KEY = "products/1/details/old.jpg";
    private static final String NEW_OBJECT_KEY = "products/1/details/new.jpg";
    private static final String NEW_IMAGE_URL = "https://images.example.com/" + NEW_OBJECT_KEY;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private S3imageStorage s3imageStorage;

    @Mock
    private S3TransactionCleanup s3TransactionCleanup;

    @Mock
    private MultipartFile imageFile;

    @InjectMocks
    private ImageService imageService;

    @Test
    void saveImage() { //이미지 등록 성공
        ProductEntity product = product();
        when(product.getProductId()).thenReturn(PRODUCT_ID);
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(imageRepository.countByProductProductId(PRODUCT_ID)).thenReturn(0L);
        when(s3imageStorage.upload(imageFile, "products/1/details")).thenReturn(NEW_OBJECT_KEY);
        when(imageRepository.save(any(ImageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(s3imageStorage.toUrl(NEW_OBJECT_KEY)).thenReturn(NEW_IMAGE_URL);

        ImageResponse response = imageService.saveImage(PRODUCT_ID, imageFile);

        assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(response.getDetailImage()).isEqualTo(NEW_IMAGE_URL);
        verify(imageRepository).save(any(ImageEntity.class));
        verify(s3TransactionCleanup).deleteAfterRollback(NEW_OBJECT_KEY);
    }

    @Test
    void saveImageRejectsMoreThanTenImages() { //10장 제한
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(imageRepository.countByProductProductId(PRODUCT_ID)).thenReturn(10L);

        assertThatThrownBy(() -> imageService.saveImage(PRODUCT_ID, imageFile))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED));

        verify(s3imageStorage, never()).upload(any(), any());
    }

    @Test
    void saveImageDeletesUploadedObjectWhenDatabaseSaveFails() { //DB 저장 실패 시 업로드 파일 삭제
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(imageRepository.countByProductProductId(PRODUCT_ID)).thenReturn(0L);
        when(s3imageStorage.upload(imageFile, "products/1/details")).thenReturn(NEW_OBJECT_KEY);
        when(imageRepository.save(any(ImageEntity.class)))
                .thenThrow(new RuntimeException("database save failed"));

        assertThatThrownBy(() -> imageService.saveImage(PRODUCT_ID, imageFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database save failed");

        verify(s3TransactionCleanup).deleteAfterRollback(NEW_OBJECT_KEY);
    }

    @Test
    void updateImage() { //이미지 수정 성공 및 기존 파일 삭제
        ImageEntity image = image();
        when(imageRepository.findByImageIdAndProductProductId(IMAGE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(image));
        when(s3imageStorage.upload(imageFile, "products/1/details")).thenReturn(NEW_OBJECT_KEY);
        when(s3imageStorage.toUrl(NEW_OBJECT_KEY)).thenReturn(NEW_IMAGE_URL);

        ImageResponse response = imageService.updateImage(PRODUCT_ID, IMAGE_ID, imageFile);

        assertThat(response.getImageId()).isEqualTo(IMAGE_ID);
        assertThat(response.getDetailImage()).isEqualTo(NEW_IMAGE_URL);
        verify(image).update(NEW_OBJECT_KEY);
        verify(imageRepository).saveAndFlush(image);
        verify(s3TransactionCleanup).deleteAfterRollback(NEW_OBJECT_KEY);
        verify(s3TransactionCleanup).deleteAfterCommit(OLD_OBJECT_KEY);
    }

    @Test
    void updateImageDeletesNewObjectWhenDatabaseUpdateFails() { //수정 실패 시 새 파일 삭제
        ImageEntity image = image();
        when(imageRepository.findByImageIdAndProductProductId(IMAGE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(image));
        when(s3imageStorage.upload(imageFile, "products/1/details")).thenReturn(NEW_OBJECT_KEY);
        doThrow(new RuntimeException("database update failed"))
                .when(imageRepository).saveAndFlush(image);

        assertThatThrownBy(() -> imageService.updateImage(PRODUCT_ID, IMAGE_ID, imageFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database update failed");

        verify(s3TransactionCleanup).deleteAfterRollback(NEW_OBJECT_KEY);
        verify(s3TransactionCleanup, never()).deleteAfterCommit(OLD_OBJECT_KEY);
    }

    @Test
    void deleteImage() { //이미지 삭제 시 DB와 S3 삭제
        ImageEntity image = image();
        when(imageRepository.findByImageIdAndProductProductId(IMAGE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(image));
        when(s3imageStorage.toUrl(OLD_OBJECT_KEY))
                .thenReturn("https://images.example.com/" + OLD_OBJECT_KEY);

        ImageResponse response = imageService.deleteImage(PRODUCT_ID, IMAGE_ID);

        assertThat(response.getImageId()).isEqualTo(IMAGE_ID);
        verify(imageRepository).delete(image);
        verify(imageRepository, never()).flush();
        verify(s3TransactionCleanup).deleteAfterCommit(OLD_OBJECT_KEY);
    }

    private ProductEntity product() {
        return org.mockito.Mockito.mock(ProductEntity.class);
    }

    private ImageEntity image() {
        ProductEntity product = product();
        org.mockito.Mockito.lenient().when(product.getProductId()).thenReturn(PRODUCT_ID);
        ImageEntity image = org.mockito.Mockito.mock(ImageEntity.class);
        org.mockito.Mockito.lenient().when(image.getImageId()).thenReturn(IMAGE_ID);
        org.mockito.Mockito.lenient().when(image.getProduct()).thenReturn(product);
        when(image.getDetailImage()).thenReturn(OLD_OBJECT_KEY);
        return image;
    }
}
