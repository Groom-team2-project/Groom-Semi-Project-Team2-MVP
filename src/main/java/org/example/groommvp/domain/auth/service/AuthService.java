package org.example.groommvp.domain.auth.service;

import org.example.groommvp.domain.auth.client.KakaoOAuthClient;
import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.*;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final AuthMemberService authMemberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final OAuthStateService oAuthStateService;

    public LoginResponse loginWithKakao(String code, String redirectUri, String state, String nonce) {
        oAuthStateService.validateAndConsume(state, nonce);

        KakaoUserInfo userInfo = kakaoOAuthClient.getUserInfo(code, redirectUri);

        AuthMemberService.MemberLookupResult lookupResult = authMemberService.findOrCreateMember(userInfo);
        String accessToken = jwtTokenProvider.createAccessToken(lookupResult.member());

        return new LoginResponse(
                "Bearer",
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                lookupResult.member().getMemberId(),
                lookupResult.member().getRole(),
                lookupResult.newMember()
        );
    }

    public KakaoAuthorizeResult getKakaoAuthorizeUrl() {
        OAuthState oAuthState = oAuthStateService.issueState();

        String url = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", kakaoOAuthProperties.clientId())
                .queryParam("redirect_uri", kakaoOAuthProperties.redirectUri())
                .queryParam("state", oAuthState.state())
                .build()
                .toUriString();

        return new KakaoAuthorizeResult(url, oAuthState.state(), oAuthState.nonce());
    }
}
