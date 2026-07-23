package org.example.groommvp.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 프로필 수정 요청 DTO.
 *
 * <pre>
 * PATCH /api/v1/members/me
 * { "email": "new@example.com", "nickname": "새닉네임" }
 * </pre>
 *
 * <p>부분 수정(PATCH)이라 두 필드 모두 선택이다. 값을 준 필드만 반영되고,
 * null/공백은 무시된다. ({@code MemberEntity#updateProfile})
 */
@Schema(description = "회원 프로필 수정 요청")
public record MemberUpdateRequest(
        @Schema(description = "변경할 이메일 (선택)", example = "new@example.com", nullable = true)
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @Schema(description = "변경할 닉네임 (선택)", example = "새닉네임", nullable = true)
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname
) {
}
