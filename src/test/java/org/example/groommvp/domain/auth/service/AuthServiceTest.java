package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.groommvp.domain.auth.client.KakaoOAuthClient;
import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.KakaoAuthorizeUrlResponse;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private AuthMemberService authMemberService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OAuthStateService oAuthStateService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        KakaoOAuthProperties properties = new KakaoOAuthProperties(
                "kakao-client-id",
                "kakao-client-secret",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "https://kauth.kakao.com/oauth/token",
                "https://kauth.kakao.com"
        );
        authService = new AuthService(
                kakaoOAuthClient,
                authMemberService,
                jwtTokenProvider,
                properties,
                oAuthStateService
        );
    }

    @Test
    void getKakaoAuthorizeUrlIncludesIssuedState() {
        when(oAuthStateService.issueState()).thenReturn("issued-state");

        KakaoAuthorizeUrlResponse response = authService.getKakaoAuthorizeUrl();
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(response.url()).build();

        assertThat(response.state()).isEqualTo("issued-state");
        assertThat(uriComponents.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(uriComponents.getQueryParams().getFirst("client_id")).isEqualTo("kakao-client-id");
        assertThat(uriComponents.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8080/api/v1/auth/kakao/callback");
        assertThat(uriComponents.getQueryParams().getFirst("state")).isEqualTo("issued-state");
    }

    @Test
    void loginWithKakaoValidatesStateBeforeRequestingKakaoUserInfo() {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Invalid OAuth state."))
                .when(oAuthStateService)
                .validateAndConsume("invalid-state");

        assertThatThrownBy(() -> authService.loginWithKakao(
                "authorization-code",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "invalid-state"
        )).isInstanceOf(BusinessException.class);

        verify(kakaoOAuthClient, never()).getUserInfo(
                "authorization-code",
                "http://localhost:8080/api/v1/auth/kakao/callback"
        );
    }
}
