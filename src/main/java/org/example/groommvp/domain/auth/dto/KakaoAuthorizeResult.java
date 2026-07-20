package org.example.groommvp.domain.auth.dto;

public record KakaoAuthorizeResult(
        String url,
        String state,
        String nonce
) {
}
