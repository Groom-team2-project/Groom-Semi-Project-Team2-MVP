package org.example.groommvp.domain.auth.controller;

import org.example.groommvp.domain.auth.dto.KakaoLoginRequest;
import org.example.groommvp.domain.auth.dto.LoginResponse;
import org.example.groommvp.domain.auth.service.AuthService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 회원을 조회/생성하고 자체 JWT를 발급합니다.")
    @PostMapping("/kakao/login")
    public ResponseEntity<CommonResponse<LoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        LoginResponse response = authService.loginWithKakao(request.code(), request.redirectUri());
        return ResponseEntity.ok(CommonResponse.success(response, "카카오 로그인 성공"));
    }
}
