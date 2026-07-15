package org.example.groommvp.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationSeconds
) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret 설정이 필요합니다. .env 등을 통해 값을 주입하세요.");
        }
        if (accessTokenExpirationSeconds <= 0) {
            accessTokenExpirationSeconds = 7200;
        }
    }
}
