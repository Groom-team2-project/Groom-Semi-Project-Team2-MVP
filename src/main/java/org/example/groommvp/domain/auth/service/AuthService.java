package org.example.groommvp.domain.auth.service;

import org.example.groommvp.domain.auth.client.KakaoOAuthClient;
import org.example.groommvp.domain.auth.dto.KakaoUserInfo;
import org.example.groommvp.domain.auth.dto.LoginResponse;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse loginWithKakao(String code, String redirectUri) {
        KakaoUserInfo userInfo = kakaoOAuthClient.getUserInfo(code, redirectUri);

        MemberLookupResult lookupResult = findOrCreateMember(userInfo);
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

    private MemberLookupResult findOrCreateMember(KakaoUserInfo userInfo) {
        return memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, userInfo.providerId())
                .map(member -> {
                    member.updateProfile(userInfo.email(), userInfo.nickname());
                    return new MemberLookupResult(member, false);
                })
                .orElseGet(() -> {
                    MemberEntity member = MemberEntity.createKakaoMember(
                            userInfo.providerId(),
                            userInfo.email(),
                            userInfo.nickname()
                    );
                    return new MemberLookupResult(memberRepository.save(member), true);
                });
    }

    private record MemberLookupResult(
            MemberEntity member,
            boolean newMember
    ) {
    }
}
