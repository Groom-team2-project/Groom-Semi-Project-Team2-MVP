package org.example.groommvp.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Swagger 문서화 전용 클래스. 실제 로직에서는 사용되지 않습니다. */
@Schema(description = "상품 목록 페이지네이션 응답")
public class ProductPageResponse extends PageResponse<ProductListResponse> {
    ProductPageResponse() {
        super(null, 0, 0, 0L);
    }
}
