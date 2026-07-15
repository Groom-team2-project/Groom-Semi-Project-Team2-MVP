package org.example.groommvp.domain.auth.dto;

public record KakaoUserInfo(
        String providerId,
        String email,
        String nickname
) {
}
