package org.example.groommvp.domain.auth.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.KakaoAuthorizeResult;
import org.example.groommvp.domain.auth.dto.LoginResponse;
import org.example.groommvp.domain.auth.service.AuthService;
import org.example.groommvp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KakaoOAuthProperties properties = new KakaoOAuthProperties(
                "kakao-client-id",
                "kakao-client-secret",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "https://kauth.kakao.com/oauth/token",
                "https://kauth.kakao.com"
        );
        AuthController authController = new AuthController(authService, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void authorizeUrlSetsOAuthNonceCookie() throws Exception {
        when(authService.getKakaoAuthorizeUrl())
                .thenReturn(new KakaoAuthorizeResult("https://kauth.kakao.com/oauth/authorize?state=state", "state", "nonce"));

        mockMvc.perform(get("/api/v1/auth/kakao/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("oauth_nonce", "nonce"))
                .andExpect(cookie().httpOnly("oauth_nonce", true))
                .andExpect(cookie().maxAge("oauth_nonce", 300))
                .andExpect(jsonPath("$.data.url").value("https://kauth.kakao.com/oauth/authorize?state=state"))
                .andExpect(jsonPath("$.data.state").value("state"));
    }

    @Test
    void callbackPassesStateAndNonceThenExpiresCookie() throws Exception {
        when(authService.loginWithKakao(
                "code",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "state",
                "nonce"
        )).thenReturn(new LoginResponse("Bearer", "access-token", 7200L, 1L, MemberRole.USER, false));

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "code")
                        .param("state", "state")
                        .cookie(new jakarta.servlet.http.Cookie("oauth_nonce", "nonce")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("oauth_nonce", 0))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        verify(authService).loginWithKakao(
                "code",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "state",
                "nonce"
        );
    }
}
