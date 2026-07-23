package org.example.groommvp.domain.event.dto;

public record FirstComeEventParticipateResponse(
        Long eventId, // 어떤 이벤트에 참여했는지
        Long memberId, // 어떤 회원이 참여했는지
        int remainingCount // 참여 후 남은 수량
) {
}