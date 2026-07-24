package org.example.groommvp.domain.auth.service;

import org.example.groommvp.domain.auth.client.KakaoOAuthClient;
import org.example.groommvp.domain.auth.config.KakaoOAuthProperties;
import org.example.groommvp.domain.auth.dto.*;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
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
    private final RefreshTokenService refreshTokenService;
    private final MemberRepository memberRepository;

    public LoginResponse loginWithKakao(String code, String redirectUri, String state, String nonce) {
        oAuthStateService.validateAndConsume(state, nonce);

        KakaoUserInfo userInfo = kakaoOAuthClient.getUserInfo(code, redirectUri);

        AuthMemberService.MemberLookupResult lookupResult = authMemberService.findOrCreateMember(userInfo);
        String accessToken = jwtTokenProvider.createAccessToken(lookupResult.member());
        String refreshToken = refreshTokenService.issue(lookupResult.member().getMemberId());

        return new LoginResponse(
                "Bearer",
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                refreshToken,
                refreshTokenService.getRefreshTokenExpirationSeconds(),
                lookupResult.member().getMemberId(),
                lookupResult.member().getRole(),
                lookupResult.newMember()
        );
    }

    public TokenReissueResponse reissue(String refreshToken) {
        Long memberId = refreshTokenService.validateAndGetMemberId(refreshToken);

        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "회원을 찾을 수 없습니다."));

        if (!member.isLoginAllowed()) {
            refreshTokenService.revoke(refreshToken);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인할 수 없는 회원 상태입니다. 관리자에게 문의하세요.");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(member);
        String newRefreshToken = refreshTokenService.rotate(refreshToken, member.getMemberId());

        return new TokenReissueResponse(
                "Bearer",
                newAccessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                newRefreshToken,
                refreshTokenService.getRefreshTokenExpirationSeconds()
        );
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
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
