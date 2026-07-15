package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.example.groommvp.domain.cart.entity.CartEntity;

/**
 * 장바구니 조회 응답 DTO.
 *
 * <p>항목 목록과 함께 총 수량/총 금액 요약을 포함한다.
 */
@Getter
@Builder
@Schema(description = "장바구니 조회 응답")
public class CartResponse {

    @Schema(description = "장바구니 ID", example = "1")
    private final Long cartId;
    @Schema(description = "회원 ID", example = "100")
    private final Long memberId;
    @Schema(description = "장바구니 항목 목록")
    private final List<CartItemResponse> items;
    @Schema(description = "총 담긴 수량 합계", example = "5")
    private final int totalQuantity;
    @Schema(description = "총 금액 합계", example = "12000")
    private final int totalPrice;

    public static CartResponse from(CartEntity cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        int totalQuantity = items.stream().mapToInt(CartItemResponse::getQuantity).sum();
        int totalPrice = items.stream().mapToInt(CartItemResponse::getLineTotal).sum();
        return CartResponse.builder()
                .cartId(cart.getCartId())
                .memberId(cart.getMemberId())
                .items(items)
                .totalQuantity(totalQuantity)
                .totalPrice(totalPrice)
                .build();
    }
}
