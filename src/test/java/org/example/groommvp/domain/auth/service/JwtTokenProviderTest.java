package org.example.groommvp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.groommvp.domain.auth.config.JwtProperties;
import org.example.groommvp.domain.auth.dto.JwtClaims;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                new JwtProperties("test-jwt-secret-key", 7200)
        );
    }

    @Test
    void validateTokenReturnsTrueForValidToken() {
        String token = jwtTokenProvider.createAccessToken(createMember(1L));

        boolean valid = jwtTokenProvider.validateToken(token);

        assertThat(valid).isTrue();
    }

    @Test
    void validateTokenReturnsFalseForBlankToken() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken("   ")).isFalse();
    }

    @Test
    void validateTokenReturnsFalseForTamperedToken() {
        String token = jwtTokenProvider.createAccessToken(createMember(1L));
        String tamperedToken = token.substring(0, token.length() - 1) + "x";

        boolean valid = jwtTokenProvider.validateToken(tamperedToken);

        assertThat(valid).isFalse();
    }

    @Test
    void getValidClaimsReturnsTokenClaims() {
        String token = jwtTokenProvider.createAccessToken(createMember(1L));

        JwtClaims claims = jwtTokenProvider.getValidClaims(token);

        assertThat(claims.memberId()).isEqualTo(1L);
        assertThat(claims.role()).isEqualTo(MemberRole.USER);
        assertThat(claims.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(claims.issuedAt()).isBeforeOrEqualTo(claims.expiresAt());
    }

    @Test
    void getValidClaimsThrowsExceptionForInvalidToken() {
        assertThatThrownBy(() -> jwtTokenProvider.getValidClaims("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getMemberIdAndRoleReturnClaimsValues() {
        String token = jwtTokenProvider.createAccessToken(createMember(10L));

        Long memberId = jwtTokenProvider.getMemberId(token);
        MemberRole role = jwtTokenProvider.getRole(token);

        assertThat(memberId).isEqualTo(10L);
        assertThat(role).isEqualTo(MemberRole.USER);
    }

    private MemberEntity createMember(Long memberId) {
        MemberEntity member = MemberEntity.createKakaoMember(
                "kakao-provider-id",
                "member@example.com",
                "member"
        );
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return member;
    }
}
