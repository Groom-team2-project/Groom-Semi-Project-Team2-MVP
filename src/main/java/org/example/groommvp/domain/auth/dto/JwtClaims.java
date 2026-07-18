package org.example.groommvp.domain.auth.dto;

import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberRole;

import java.time.Instant;

public record JwtClaims(
        Long memberId,
        MemberRole role,
        AuthProvider provider,
        Instant issuedAt,
        Instant expiresAt
) {
}
