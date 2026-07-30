package org.example.groommvp.global.storage;

import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class S3imageStorageTest {

    @Mock
    private S3Client s3Client;

    private S3imageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3imageStorage(s3Client);
        ReflectionTestUtils.setField(storage, "bucket", "test-bucket");
    }

    @Test
    void deleteConvertsS3FailureToBusinessException() {
        S3Exception cause = (S3Exception) S3Exception.builder()
                .statusCode(500)
                .message("S3 delete failed")
                .build();
        doThrow(cause).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> storage.delete("products/1/image.jpg"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IMAGE_DELETE_FAILED);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    void deleteQuietlyDoesNotHideOriginalFailureWithAnotherException() {
        doThrow(S3Exception.builder().statusCode(500).message("S3 delete failed").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatCode(() -> storage.deleteQuietly("products/1/image.jpg"))
                .doesNotThrowAnyException();
    }
}
