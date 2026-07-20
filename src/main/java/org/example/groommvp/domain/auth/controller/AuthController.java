package org.example.groommvp.domain.auth.controller;

import java.time.Duration;

import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.config.OAuthCookieProperties;
import org.example.groommvp.domain.auth.dto.KakaoAuthorizeResult;
import org.example.groommvp.domain.auth.dto.KakaoAuthorizeUrlResponse;
import org.example.groommvp.domain.auth.dto.KakaoLoginRequest;
import org.example.groommvp.domain.auth.dto.LoginResponse;
import org.example.groommvp.domain.auth.service.AuthService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "Auth API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final OAuthCookieProperties oAuthCookieProperties;

    @Operation(summary = "카카오 OAuth 로그인", description = "카카오 인가 코드로 회원 조회/생성 후 자체 JWT 발급")
    @PostMapping("/kakao/login")
    public ResponseEntity<CommonResponse<LoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request,
            @CookieValue(name = "oauth_nonce", required = false) String nonce
    ) {
        LoginResponse response = authService.loginWithKakao(
                request.code(),
                request.redirectUri(),
                request.state(),
                nonce
        );
        ResponseCookie expiredCookie = createOAuthNonceCookie("", Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(CommonResponse.success(response, "카카오 로그인 성공"));
    }

    @Operation(summary = "카카오 OAuth 인증 URL 조회", description = "카카오 로그인 URL을 반환하고, OAuth nonce 쿠키 설정")
    @GetMapping("/kakao/authorize-url")
    public ResponseEntity<CommonResponse<KakaoAuthorizeUrlResponse>> authorizeUrl() {
        KakaoAuthorizeResult result = authService.getKakaoAuthorizeUrl();
        KakaoAuthorizeUrlResponse response = new KakaoAuthorizeUrlResponse(result.url(), result.state());
        ResponseCookie cookie = createOAuthNonceCookie(result.nonce(), Duration.ofMinutes(5));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(CommonResponse.success(response, "카카오 로그인 URL 조회 성공"));
    }

    /*
     * 프론트엔드 구현시 아래 Callback API는 사용하지 않아도 될 듯 합니다.
     * 구현시 카카오 로그인 설정에서 callback url을 프론트엔드 쪽으로 변경하고, 해당 메서드는 삭제해도 될 것 같습니다.
     */
    @Operation(summary = "카카오 OAuth Callback", description = "카카오 OAuth 로그인 처리를 위한 임시 콜백 API")
    @GetMapping("/kakao/callback")
    public ResponseEntity<CommonResponse<LoginResponse>> kakaoCallback(
            @RequestParam String code,
            @RequestParam String state,
            @CookieValue(name = "oauth_nonce", required = false) String nonce
    ) {
        LoginResponse response = authService.loginWithKakao(
                code,
                kakaoOAuthProperties.redirectUri(),
                state,
                nonce
        );
        ResponseCookie expiredCookie = createOAuthNonceCookie("", Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(CommonResponse.success(response, "Kakao login succeeded"));
    }

    private ResponseCookie createOAuthNonceCookie(String value, Duration maxAge) {
        return ResponseCookie.from("oauth_nonce", value)
                .httpOnly(true)
                .secure(oAuthCookieProperties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
