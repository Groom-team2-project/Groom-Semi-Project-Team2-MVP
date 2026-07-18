package org.example.groommvp.domain.auth.security;

import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberRole;

public record AuthMember(
        Long memberId,
        MemberRole role,
        AuthProvider provider
) {
}
