package org.example.groommvp.domain.event.service;

import java.util.concurrent.TimeUnit;

import org.example.groommvp.domain.event.dto.FirstComeEventParticipateResponse;
import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.example.groommvp.domain.event.entity.FirstComeEventParticipant;
import org.example.groommvp.domain.event.repository.FirstComeEventParticipantRepository;
import org.example.groommvp.domain.event.repository.FirstComeEventRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FirstComeEventService {
    private final RedissonClient redissonClient;
    private final FirstComeEventRepository eventRepository;
    private final FirstComeEventParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;

    public FirstComeEventParticipateResponse participate(Long eventId, Long memberId) {
        RLock lock = redissonClient.getLock("event:" +  eventId + ":lock"); // 이벤트별로 락 이름 생성

        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS); // 최대 3초 기다리고, 락을 잡으면 10초 뒤 자동 만료
            if (!locked) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            return transactionTemplate.execute(status -> {
                FirstComeEvent event = eventRepository.findById(eventId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

                // 같은 회원이 이미 참여했는지 확인
                if (participantRepository.existsByEventIdAndMemberId(eventId, memberId)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }

                event.participate(); // 남은 수량이 있으면 참여 수를 1 증가시킴
                participantRepository.save(new FirstComeEventParticipant(event, memberId)); // 참여 기록을 DB에 저장

                // 참여 성공 후 eventId, memberId, 남은 수량을 응답으로 돌려줌
                return new FirstComeEventParticipateResponse(
                        event.getId(),
                        memberId,
                        event.getRemainingCount()
                );
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock(); // 성공/실패와 상관없이 잡은 락은 다시 풀어줌
            }
        }
    }
}
