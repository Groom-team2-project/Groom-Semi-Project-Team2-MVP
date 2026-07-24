package org.example.groommvp.domain.auth.dto;

import org.example.groommvp.domain.member.entity.MemberRole;

public record LoginResponse(
        String tokenType,
        String accessToken,
        Long expiresIn,
        String refreshToken,
        Long refreshTokenExpiresIn,
        Long memberId,
        MemberRole role,
        boolean newMember
) {
}
