package org.example.groommvp.domain.auth.controller;

import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.KakaoAuthorizeUrlResponse;
import org.example.groommvp.domain.auth.dto.KakaoLoginRequest;
import org.example.groommvp.domain.auth.dto.LoginResponse;
import org.example.groommvp.domain.auth.service.AuthService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KakaoOAuthProperties kakaoOAuthProperties;

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 회원을 조회/생성하고 자체 JWT를 발급합니다.")
    @PostMapping("/kakao/login")
    public ResponseEntity<CommonResponse<LoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        LoginResponse response = authService.loginWithKakao(request.code(), request.redirectUri());
        return ResponseEntity.ok(CommonResponse.success(response, "카카오 로그인 성공"));
    }

    @Operation(summary = "카카오 로그인 페이지 제공", description = "카카오 로그인 페이지를 사용자에게 제공합니다.")
    @GetMapping("/kakao/authorize-url")
    public ResponseEntity<CommonResponse<KakaoAuthorizeUrlResponse>> authorizeUrl() {
        KakaoAuthorizeUrlResponse response = authService.getKakaoAuthorizeUrl();

        return ResponseEntity.ok(
                CommonResponse.success(response, "카카오 로그인 URL 조회 성공")
        );
    }

    /*
    * 프론트엔드가 없어 임시로 callback 메서드를 제작하여 JWT 토큰을 JSON으로 받아내는 API입니다.
    * 프론트엔드가 만들어질 경우 이 API는 제거 하는 것이 좋을거 같습니다.
     */
    @GetMapping("/kakao/callback")
    public ResponseEntity<CommonResponse<LoginResponse>> kakaoCallback(
            @RequestParam String code
    ) {
        LoginResponse response = authService.loginWithKakao(
                code,
                kakaoOAuthProperties.redirectUri()
        );

        return ResponseEntity.ok(
                CommonResponse.success(response, "카카오 로그인 성공")
        );
    }
}
