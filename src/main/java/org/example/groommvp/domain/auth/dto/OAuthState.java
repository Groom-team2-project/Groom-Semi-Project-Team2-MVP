package org.example.groommvp.domain.auth.dto;

public record OAuthState(
        String state,
        String nonce
) {
}
