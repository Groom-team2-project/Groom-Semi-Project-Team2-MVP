package org.example.groommvp.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationSeconds
) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            // TODO: .env를 통해 secret Key 관리할 것.
            // 이후 배포 이전에 모든 Secret Key는 .env에 저장해야합니다.
            secret = "local-dev-jwt-secret-key-change-before-deploy";
        }
        if (accessTokenExpirationSeconds <= 0) {
            accessTokenExpirationSeconds = 7200;
        }
    }
}
