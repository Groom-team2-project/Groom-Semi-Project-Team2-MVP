package org.example.groommvp.domain.product.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ImageCreateRequest;
import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.service.ImageService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product_image", description = "상품 사진 관리")
@RestController
@RequestMapping("/api/v1/products/{productId}/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping
    public ResponseEntity<CommonResponse<ImageResponse>> createImage(
            @PathVariable Long productId,
            @Valid @RequestBody ImageCreateRequest request) {
        ImageResponse response = imageService.saveImage(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "이미지가 저장되었습니다."));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId) {
        imageService.deleteImage(productId);
        return ResponseEntity.noContent().build();
    }
}
