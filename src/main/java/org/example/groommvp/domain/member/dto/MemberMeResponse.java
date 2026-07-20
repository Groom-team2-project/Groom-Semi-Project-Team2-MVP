package org.example.groommvp.domain.member.dto;

import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.entity.MemberRole;
import org.example.groommvp.domain.member.entity.MemberStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 회원 정보 조회 응답")
public record MemberMeResponse(
        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "인증 제공자", example = "KAKAO")
        AuthProvider provider,

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "닉네임", example = "홍길동")
        String nickname,

        @Schema(description = "회원 역할", example = "USER")
        MemberRole role,

        @Schema(description = "회원 상태", example = "ACTIVE")
        MemberStatus status
) {

    public static MemberMeResponse from(MemberEntity member) {
        return new MemberMeResponse(
                member.getMemberId(),
                member.getProvider(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getStatus()
        );
    }
}
