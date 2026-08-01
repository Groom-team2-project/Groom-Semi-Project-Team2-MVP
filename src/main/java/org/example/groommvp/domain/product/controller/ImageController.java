package org.example.groommvp.domain.product.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.ImageResponse;
import org.example.groommvp.domain.product.service.ImageService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Product_image", description = "상품 사진 관리")
@RestController
@RequestMapping("/api/v1/products/{productId}/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<ImageResponse>> createDetailImages(
            @PathVariable Long productId,
            @RequestPart("image")MultipartFile image) {
        ImageResponse response = imageService.saveImage(productId, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "이미지가 저장되었습니다."));
    }

    @PutMapping(value = "/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<ImageResponse>> updateImages(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestPart("image")MultipartFile image) {
        ImageResponse response = imageService.updateImage(productId, imageId, image);
        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(response, "이미지가 수정되었습니다."));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<CommonResponse<ImageResponse>> deleteImages(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        ImageResponse response = imageService.deleteImage(productId, imageId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(response, "이미지가 삭제되었습니다."));
    }
}
