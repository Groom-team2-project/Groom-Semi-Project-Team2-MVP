package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 장바구니 주문(체크아웃) 응답 DTO.
 *
 * <p>장바구니 전체를 하나의 주문으로 전환한 결과를 담는다. (E→C→D 구매 흐름의 시작점)
 */
@Schema(description = "장바구니 주문 응답")
public record CartCheckoutResponse(
        @Schema(description = "생성된 주문 ID", example = "42")
        Long orderId,
        @Schema(description = "주문된 상품 항목")
        List<OrderedItem> items,
        @Schema(description = "총 주문 금액", example = "12000")
        Long totalPrice,
        @Schema(type = "string", description = "주문 생성 시각 (ISO-8601 LocalDateTime, 예: 2024-01-15T10:30:00)",
                example = "2024-01-15T10:30:00")
        LocalDateTime orderedAt
) {

    @Schema(description = "주문된 개별 상품")
    public record OrderedItem(
            @Schema(description = "상품 ID", example = "1")
            Long productId,
            @Schema(description = "주문 수량", example = "2")
            int quantity,
            @Schema(description = "주문 시점 단가", example = "1000")
            int orderPrice,
            @Schema(description = "차감 후 남은 재고", example = "48")
            int remainingStock
    ) {
    }
}
