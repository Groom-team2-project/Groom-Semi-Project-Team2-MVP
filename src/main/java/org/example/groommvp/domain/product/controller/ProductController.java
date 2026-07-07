package org.example.groommvp.domain.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.product.dto.*;
import org.example.groommvp.domain.product.service.ProductService;
import org.example.groommvp.global.response.CommonResponse;
import org.example.groommvp.global.response.ErrorResponse;
import org.example.groommvp.global.response.SwaggerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product", description = "상품 관리 API")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다. 상품 등록 시 초기 재고 수량도 함께 설정됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "상품 등록 성공",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "400", description = "입력값 유효성 검사 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INVALID_INPUT_VALUE",
                                        "message": "입력값이 올바르지 않습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다."
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<Void> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "상품 단건 조회", description = "상품 ID로 특정 상품의 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.ProductCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": {
                                            "productName": "MacBook Pro",
                                            "productPrice": 2500000,
                                            "stocks": 50
                                        },
                                        "errorCode": null,
                                        "message": null
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "상품 또는 재고 정보를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "상품 없음", value = """
                                            {
                                                "success": false,
                                                "data": null,
                                                "errorCode": "PRODUCT_NOT_FOUND",
                                                "message": "상품을 찾을 수 없습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "재고 정보 없음", value = """
                                            {
                                                "success": false,
                                                "data": null,
                                                "errorCode": "STOCK_NOT_FOUND",
                                                "message": "재고를 찾을 수 없습니다."
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다."
                                    }
                                    """)))
    })
    @GetMapping("/{productId}")
    public ResponseEntity<CommonResponse<ProductResponse>> getProduct(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(CommonResponse.success(response, null));
    }

    @Operation(summary = "상품 수정", description = "상품명과 가격을 수정합니다. 재고 수량은 별도의 입고 API를 통해 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "400", description = "입력값 유효성 검사 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INVALID_INPUT_VALUE",
                                        "message": "입력값이 올바르지 않습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "PRODUCT_NOT_FOUND",
                                        "message": "상품을 찾을 수 없습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다."
                                    }
                                    """)))
    })
    @PutMapping("/{productId}")
    public ResponseEntity<Void> updateProduct(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        productService.updateProduct(productId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "PRODUCT_NOT_FOUND",
                                        "message": "상품을 찾을 수 없습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다."
                                    }
                                    """)))
    })
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "상품 ID", example = "1", required = true)
            @PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }
  
      // 검색
    @Operation(summary = "상품 목록 조회", description = "상품 목록을 페이지네이션으로 조회합니다. keyword 파라미터로 상품명 검색이 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SwaggerResponse.ProductPageCommonResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "data": {
                                            "content": [
                                                {"productId": 1, "productName": "MacBook Pro", "productPrice": 2500000},
                                                {"productId": 2, "productName": "iPhone 16", "productPrice": 1200000}
                                            ],
                                            "page": 0,
                                            "size": 10,
                                            "totalElements": 2
                                        },
                                        "errorCode": null,
                                        "message": "상품 목록 조회 성공"
                                    }
                                    """))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "data": null,
                                        "errorCode": "INTERNAL_SERVER_ERROR",
                                        "message": "서버 내부 오류가 발생했습니다."
                                    }
                                    """)))
    })
    @GetMapping("")
    public ResponseEntity<CommonResponse<PageResponse<ProductListResponse>>> getProducts(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "상품명 검색 키워드 (선택)")
            @RequestParam(required = false) String keyword
    ){
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductListResponse> productPage = productService.getProductList(keyword, pageable);

        PageResponse<ProductListResponse> pageData = PageResponse.from(productPage);

        return ResponseEntity.ok(CommonResponse.success(pageData, "상품 목록 조회 성공"));
    }
}
