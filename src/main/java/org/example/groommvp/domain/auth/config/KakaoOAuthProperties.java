package org.example.groommvp.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.oauth")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenUri,
        String issuerUri
) {
    public KakaoOAuthProperties {
        if (tokenUri == null || tokenUri.isBlank()) {
            tokenUri = "https://kauth.kakao.com/oauth/token";
        }
        if (issuerUri == null || issuerUri.isBlank()) {
            issuerUri = "https://kauth.kakao.com";
        }
    }
}
