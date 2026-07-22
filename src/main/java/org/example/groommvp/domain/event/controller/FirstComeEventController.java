package org.example.groommvp.domain.event.controller;

import org.example.groommvp.domain.auth.security.AuthMember;
import org.example.groommvp.domain.event.dto.FirstComeEventParticipateResponse;
import org.example.groommvp.domain.event.service.FirstComeEventService;
import org.example.groommvp.global.response.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class FirstComeEventController {
    private final FirstComeEventService firstComeEventService;

    @PostMapping("/{eventId}/participate")
    public ResponseEntity<CommonResponse<FirstComeEventParticipateResponse>> participate(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthMember authMember
    ) {
        // 서비스에 이벤트 ID와 로그인 회원 ID를 넘김
        FirstComeEventParticipateResponse response = firstComeEventService.participate(
                eventId,
                authMember.memberId()
        );

        return ResponseEntity.ok(
                CommonResponse.success(response, "선착순 이벤트 참여 성공")
        );
    }
}
