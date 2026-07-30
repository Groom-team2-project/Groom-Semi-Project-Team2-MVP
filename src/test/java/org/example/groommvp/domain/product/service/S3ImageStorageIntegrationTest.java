package org.example.groommvp.domain.product.service;

import org.example.groommvp.global.storage.S3imageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_S3_INTEGRATION_TEST", matches = "true")
class S3ImageStorageIntegrationTest {

    private static final String DEFAULT_BUCKET = "goorm-dev-mmy";
    private static final String DEFAULT_REGION = "us-west-2";
    private static final String DEFAULT_PUBLIC_BASE_URL =
            "https://goorm-dev-mmy.s3.us-west-2.amazonaws.com";

    // 1x1 transparent PNG
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF"
                    + "gAI/6pWfWQAAAABJRU5ErkJggg=="
    );

    @Test
    void uploadsReadsAndDeletesRealS3Image() throws Exception {
        String bucket = environmentOrDefault("AWS_S3_BUCKET", DEFAULT_BUCKET);
        String region = environmentOrDefault("AWS_REGION", DEFAULT_REGION);
        String publicBaseUrl =
                environmentOrDefault("IMAGE_PUBLIC_BASE_URL", DEFAULT_PUBLIC_BASE_URL);

        try (S3Client s3Client = S3Client.builder().region(Region.of(region)).build()) {
            S3imageStorage storage = new S3imageStorage(s3Client);
            ReflectionTestUtils.setField(storage, "bucket", bucket);
            ReflectionTestUtils.setField(storage, "publicBaseUrl", publicBaseUrl);

            MockMultipartFile image = new MockMultipartFile(
                    "image",
                    "codex-s3-verification.png",
                    "image/png",
                    PNG
            );

            String objectKey = null;
            String verifiedPublicUrl = null;
            try {
                objectKey = storage.upload(image, "codex-verification");
                String verifiedObjectKey = objectKey;

                assertThat(s3Client.headObject(request -> request
                        .bucket(bucket)
                        .key(verifiedObjectKey)).contentLength()).isEqualTo((long) PNG.length);

                String publicUrl = storage.toUrl(verifiedObjectKey);
                verifiedPublicUrl = publicUrl;
                HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create(publicUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("content-type").orElse(""))
                        .startsWith("image/png");
                assertThat(response.body()).isEqualTo(PNG);

                Path proofImage = Path.of("build", "s3-verification.png");
                Files.createDirectories(proofImage.getParent());
                Files.write(proofImage, response.body());
            } finally {
                if (objectKey != null) {
                    String deletedObjectKey = objectKey;
                    storage.delete(deletedObjectKey);

                    assertThatThrownBy(() -> s3Client.headObject(request -> request
                            .bucket(bucket)
                            .key(deletedObjectKey)))
                            .isInstanceOfSatisfying(S3Exception.class,
                                    exception -> assertThat(exception.statusCode()).isEqualTo(404));

                    if (verifiedPublicUrl != null) {
                        Files.writeString(Path.of("build", "s3-verification.txt"),
                                "Upload, S3 HEAD, public HTTP GET, and verified cleanup succeeded.\n"
                                        + "Verified URL before cleanup: " + verifiedPublicUrl + "\n");
                    }
                }
            }
        }
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
