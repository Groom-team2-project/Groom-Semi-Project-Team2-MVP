package org.example.groommvp.performance;

import org.example.groommvp.domain.event.dto.FirstComeEventParticipateResponse;
import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.example.groommvp.domain.event.repository.FirstComeEventParticipantRepository;
import org.example.groommvp.domain.event.repository.FirstComeEventRepository;
import org.example.groommvp.domain.event.service.FirstComeEventService;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Validated
@Profile("performance")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/performance/events")
public class EventLockPerformanceController {

    private final FirstComeEventService eventService;
    private final FirstComeEventRepository eventRepository;
    private final FirstComeEventParticipantRepository participantRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<EventStateResponse> create(
            @Valid @RequestBody CreateEventRequest request
    ) {
        FirstComeEvent event = eventRepository.save(
                new FirstComeEvent("lock-performance-test", request.limitCount())
        );
        return ResponseEntity.ok(EventStateResponse.from(event, 0));
    }

    @PostMapping("/{eventId}/participate")
    public ResponseEntity<FirstComeEventParticipateResponse> participate(
            @PathVariable Long eventId,
            @RequestParam @Min(1) Long memberId
    ) {
        return ResponseEntity.ok(eventService.participate(eventId, memberId));
    }

    @GetMapping("/{eventId}")
    @Transactional(readOnly = true)
    public ResponseEntity<EventStateResponse> getState(@PathVariable Long eventId) {
        FirstComeEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        long participantCount = participantRepository.countByEventId(eventId);
        return ResponseEntity.ok(EventStateResponse.from(event, participantCount));
    }

    public record CreateEventRequest(@Min(1) int limitCount) {
    }

    public record EventStateResponse(
            Long eventId,
            int limitCount,
            int participatedCount,
            int remainingCount,
            long participantRows
    ) {
        private static EventStateResponse from(FirstComeEvent event, long participantRows) {
            return new EventStateResponse(
                    event.getId(),
                    event.getLimitCount(),
                    event.getParticipatedCount(),
                    event.getRemainingCount(),
                    participantRows
            );
        }
    }
}
