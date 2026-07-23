package org.example.groommvp.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.example.groommvp.domain.cart.entity.CartEntity;

/**
 * 장바구니 조회 응답 DTO.
 *
 * <p>항목 목록과 함께 총 수량/총 금액 요약을 포함한다.
 *
 * <p>Redis 캐시에 JSON 으로 직렬화된다. record 는 Jackson 이 별도 설정 없이
 * 역직렬화할 수 있어 캐시 값 타입으로 안전하다.
 */
@Schema(description = "장바구니 조회 응답")
public record CartResponse(
        @Schema(description = "장바구니 ID (아직 장바구니가 없으면 null)", example = "1", nullable = true)
        Long cartId,
        @Schema(description = "회원 ID", example = "100")
        Long memberId,
        @Schema(description = "장바구니 항목 목록")
        List<CartItemResponse> items,
        @Schema(description = "총 담긴 수량 합계", example = "5")
        int totalQuantity,
        @Schema(description = "총 금액 합계", example = "12000")
        int totalPrice
) {

    public static CartResponse from(CartEntity cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        int totalPrice = items.stream().mapToInt(CartItemResponse::lineTotal).sum();
        return new CartResponse(
                cart.getCartId(),
                cart.getMember().getMemberId(),
                items,
                totalQuantity,
                totalPrice
        );
    }

    /** 아직 장바구니가 생성되지 않은 회원의 빈 응답. */
    public static CartResponse empty(Long memberId) {
        return new CartResponse(null, memberId, List.of(), 0, 0);
    }
}
