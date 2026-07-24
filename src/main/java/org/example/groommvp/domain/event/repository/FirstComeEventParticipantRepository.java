package org.example.groommvp.domain.event.repository;

import org.example.groommvp.domain.event.entity.FirstComeEventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

/** 참여 기록 조회/저장 담당 */
public interface FirstComeEventParticipantRepository extends JpaRepository<FirstComeEventParticipant, Long> {
    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);
}