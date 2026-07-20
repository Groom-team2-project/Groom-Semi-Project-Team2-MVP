package org.example.groommvp.domain.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.cart.dto.CartCheckoutResponse;
import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.dto.CartItemUpdateRequest;
import org.example.groommvp.domain.cart.dto.CartResponse;
import org.example.groommvp.domain.cart.service.CartOrderService;
import org.example.groommvp.domain.cart.service.CartService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 API 컨트롤러.
 *
 * <p>응답은 팀 공통 포맷({@link CommonResponse}) 으로 감싸서 반환한다.
 *
 * <p><b>인증 임시 처리:</b> 회원 인증(파트 A)이 아직 없어 회원 식별을 {@code X-Member-Id}
 * 헤더로 대신한다. 파트 A 완성 후 {@code @AuthenticationPrincipal} 로 교체한다.
 */
@Tag(name = "Cart", description = "장바구니 API")
@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    /** TODO(파트 A): 인증 도입 시 @AuthenticationPrincipal 로 교체. */
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final CartService cartService;
    private final CartOrderService cartOrderService;

    @Operation(summary = "내 장바구니 조회", description = "회원의 장바구니와 총 수량/금액 요약을 조회합니다. 장바구니가 없으면 빈 장바구니를 생성해 반환합니다.")
    @GetMapping
    public ResponseEntity<CommonResponse<CartResponse>> getMyCart(
            @Parameter(description = "회원 ID (임시 인증 헤더)", example = "100", required = true)
            @RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        CartResponse response = cartService.getMyCart(memberId);
        return ResponseEntity.ok(CommonResponse.success(response, "장바구니 조회 성공"));
    }

    @Operation(summary = "장바구니 담기", description = "상품을 장바구니에 담습니다. 이미 담긴 상품이면 수량을 더합니다.")
    @PostMapping("/items")
    public ResponseEntity<CommonResponse<CartResponse>> addItem(
            @Parameter(description = "회원 ID (임시 인증 헤더)", example = "100", required = true)
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Valid @RequestBody CartItemAddRequest request) {
        CartResponse response = cartService.addItem(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "장바구니에 담았습니다."));
    }

    @Operation(summary = "장바구니 항목 수량 변경", description = "장바구니 항목의 수량을 변경합니다. 본인 항목만 변경할 수 있습니다.")
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CommonResponse<CartResponse>> updateItem(
            @Parameter(description = "회원 ID (임시 인증 헤더)", example = "100", required = true)
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Parameter(description = "장바구니 항목 ID", example = "10", required = true)
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        CartResponse response = cartService.updateItemQuantity(memberId, cartItemId, request);
        return ResponseEntity.ok(CommonResponse.success(response, "수량을 변경했습니다."));
    }

    @Operation(summary = "장바구니 항목 삭제", description = "장바구니에서 항목을 삭제합니다. 본인 항목만 삭제할 수 있습니다.")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CommonResponse<CartResponse>> removeItem(
            @Parameter(description = "회원 ID (임시 인증 헤더)", example = "100", required = true)
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Parameter(description = "장바구니 항목 ID", example = "10", required = true)
            @PathVariable Long cartItemId) {
        CartResponse response = cartService.removeItem(memberId, cartItemId);
        return ResponseEntity.ok(CommonResponse.success(response, "항목을 삭제했습니다."));
    }

    @Operation(summary = "장바구니 비우기", description = "장바구니의 모든 항목을 삭제합니다.")
    @DeleteMapping
    public ResponseEntity<CommonResponse<CartResponse>> clearCart(
            @Parameter(description = "회원 ID (임시 인증 헤더)", example = "100", required = true)
            @RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        CartResponse response = cartService.clearCart(memberId);
        return ResponseEntity.ok(CommonResponse.success(response, "장바구니를 비웠습니다."));
    }

    @Operation(summary = "장바구니 주문", description = "장바구니 전체를 하나의 주문으로 전환하고 재고를 차감합니다. 성공 시 장바구니를 비웁니다. (E→C→D 구매 흐름)")
    @PostMapping("/checkout")
    public ResponseEntity<CommonResponse<CartCheckoutResponse>> checkout(
            @Parameter(description = "회원 ID (임시 인증 헤더)", example = "100", required = true)
            @RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        CartCheckoutResponse response = cartOrderService.checkout(memberId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(response, "장바구니 주문이 완료되었습니다."));
    }
}
