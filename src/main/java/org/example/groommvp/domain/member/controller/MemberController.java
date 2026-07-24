package org.example.groommvp.domain.member.controller;

import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.member.dto.MemberMeResponse;
import org.example.groommvp.domain.member.dto.MemberUpdateRequest;
import org.example.groommvp.domain.member.service.MemberService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "회원", description = "회원 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    @Operation(
            summary = "내 회원 정보 조회",
            description = "JWT 인증 정보를 기준으로 현재 로그인한 회원 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<MemberMeResponse>> getMe(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        MemberMeResponse response = memberService.getMe(authMember.memberId());
        return ResponseEntity.ok(CommonResponse.success(response, "내 회원 정보 조회 성공"));
    }

    @Operation(
            summary = "내 프로필 수정",
            description = "현재 로그인한 회원의 이메일/닉네임을 수정합니다. 값을 준 필드만 반영되며, null/공백은 무시됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PatchMapping("/me")
    public ResponseEntity<CommonResponse<MemberMeResponse>> updateMe(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        MemberMeResponse response = memberService.updateMe(authMember.memberId(), request);
        return ResponseEntity.ok(CommonResponse.success(response, "내 프로필 수정 성공"));
    }
}
