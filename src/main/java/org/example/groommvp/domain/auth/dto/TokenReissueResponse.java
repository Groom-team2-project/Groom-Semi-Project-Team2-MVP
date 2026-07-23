package org.example.groommvp.domain.auth.dto;

public record TokenReissueResponse(
        String tokenType,
        String accessToken,
        Long expiresIn,
        String refreshToken,
        Long refreshTokenExpiresIn
) {
}
