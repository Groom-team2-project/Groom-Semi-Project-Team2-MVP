package org.example.groommvp.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.product.entity.ImageEntity;

@Schema(description = "상품 이미지 응답")
@Getter
@Builder
public class ImageResponse {

    @Schema(description = "상품 ID")
    private final Long productId;

    @Schema(description = "이미지 ID")
    private final Long imageId;

    @Schema(description = "이미지 URL")
    private final String imageUrl;

    public static ImageResponse from(ImageEntity image) {
        return ImageResponse.builder()
                .productId(image.getProduct().getProductId())
                .imageId(image.getImageId())
                .imageUrl(image.getImageUrl())
                .build();
    }
}
