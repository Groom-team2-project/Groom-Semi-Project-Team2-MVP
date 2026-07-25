package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.groommvp.domain.auth.client.KakaoOAuthClient;
import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.KakaoAuthorizeResult;
import org.example.groommvp.domain.auth.dto.KakaoUserInfo;
import org.example.groommvp.domain.auth.dto.OAuthState;
import org.example.groommvp.domain.auth.dto.TokenReissueResponse;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

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

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private MemberRepository memberRepository;

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
                oAuthStateService,
                refreshTokenService,
                memberRepository
        );
    }

    @Test
    void getKakaoAuthorizeUrlIncludesIssuedState() {
        when(oAuthStateService.issueState()).thenReturn(new OAuthState("issued-state", "issued-nonce"));

        KakaoAuthorizeResult response = authService.getKakaoAuthorizeUrl();
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(response.url()).build();

        assertThat(response.state()).isEqualTo("issued-state");
        assertThat(response.nonce()).isEqualTo("issued-nonce");
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
                .validateAndConsume("invalid-state", "invalid-nonce");

        assertThatThrownBy(() -> authService.loginWithKakao(
                "authorization-code",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "invalid-state",
                "invalid-nonce"
        )).isInstanceOf(BusinessException.class);

        verify(kakaoOAuthClient, never()).getUserInfo(
                "authorization-code",
                "http://localhost:8080/api/v1/auth/kakao/callback"
        );
    }

    @Test
    void loginWithKakaoIssuesAccessTokenAndRefreshToken() {
        KakaoUserInfo userInfo = new KakaoUserInfo("kakao-id", "sunwoo@example.com", "sunwoo");
        MemberEntity member = MemberEntity.createKakaoMember("kakao-id", "sunwoo@example.com", "sunwoo");
        ReflectionTestUtils.setField(member, "memberId", 1L);

        when(kakaoOAuthClient.getUserInfo("authorization-code", "http://localhost:8080/callback"))
                .thenReturn(userInfo);
        when(authMemberService.findOrCreateMember(userInfo))
                .thenReturn(new AuthMemberService.MemberLookupResult(member, true));
        when(jwtTokenProvider.createAccessToken(member)).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(7200L);
        when(refreshTokenService.issue(1L)).thenReturn("refresh-token");
        when(refreshTokenService.getRefreshTokenExpirationSeconds()).thenReturn(1209600L);

        var response = authService.loginWithKakao(
                "authorization-code",
                "http://localhost:8080/callback",
                "state",
                "nonce"
        );

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(7200L);
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600L);
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.newMember()).isTrue();
    }

    @Test
    void reissueRotatesRefreshTokenAtomically() {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-id", "sunwoo@example.com", "sunwoo");
        ReflectionTestUtils.setField(member, "memberId", 1L);

        when(refreshTokenService.validateAndGetMemberId("old-refresh-token")).thenReturn(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(member)).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(7200L);
        when(refreshTokenService.rotate("old-refresh-token", 1L)).thenReturn("new-refresh-token");
        when(refreshTokenService.getRefreshTokenExpirationSeconds()).thenReturn(1209600L);

        TokenReissueResponse response = authService.reissue("old-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenService).rotate("old-refresh-token", 1L);
        verify(refreshTokenService, never()).revoke("old-refresh-token");
        verify(refreshTokenService, never()).issue(1L);
    }
}
