package org.example.groommvp.domain.event.service;

import java.util.concurrent.TimeUnit;

import org.example.groommvp.domain.event.config.EventLockProperties;
import org.example.groommvp.domain.event.config.EventLockStrategy;
import org.example.groommvp.domain.event.dto.FirstComeEventParticipateResponse;
import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.example.groommvp.domain.event.entity.FirstComeEventParticipant;
import org.example.groommvp.domain.event.repository.FirstComeEventParticipantRepository;
import org.example.groommvp.domain.event.repository.FirstComeEventRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirstComeEventService {
    private static final String EVENT_LOCK_KEY_FORMAT = "event:%d:lock";

    private final RedissonClient redissonClient;
    private final FirstComeEventRepository eventRepository;
    private final FirstComeEventParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;
    private final EventLockProperties lockProperties;

    public FirstComeEventParticipateResponse participate(Long eventId, Long memberId) {
        if (lockProperties.strategy() == EventLockStrategy.PESSIMISTIC) {
            return participateWithPessimisticLock(eventId, memberId);
        }

        return participateWithDistributedLock(eventId, memberId);
    }

    private FirstComeEventParticipateResponse participateWithDistributedLock(
            Long eventId,
            Long memberId
    ) {
        String lockKey = EVENT_LOCK_KEY_FORMAT.formatted(eventId);
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(3, TimeUnit.SECONDS);

            if (!locked) {
                throw new BusinessException(ErrorCode.EVENT_LOCK_TIMEOUT);
            }

            return participateInTransaction(eventId, memberId, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (RedisException e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("lockKey", lockKey)
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("clientShutdown", redissonClient.isShutdown())
                    .addKeyValue("clientShuttingDown", redissonClient.isShuttingDown())
                    .log("event_lock_redis_error");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } finally {
            if (locked) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (RedisException | IllegalMonitorStateException e) {
                    // 운영 로그 수집기의 error 알림 대상으로 사용한다.
                    log.atError()
                            .setCause(e)
                            .addKeyValue("lockKey", lockKey)
                            .addKeyValue("eventId", eventId)
                            .log("event_lock_unlock_failed");
                }
            }
        }
    }

    private FirstComeEventParticipateResponse participateWithPessimisticLock(
            Long eventId,
            Long memberId
    ) {
        return participateInTransaction(eventId, memberId, true);
    }

    private FirstComeEventParticipateResponse participateInTransaction(
            Long eventId,
            Long memberId,
            boolean pessimistic
    ) {
        return transactionTemplate.execute(status -> {
            FirstComeEvent event = (pessimistic
                    ? eventRepository.findByIdWithPessimisticLock(eventId)
                    : eventRepository.findById(eventId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            if (participantRepository.existsByEventIdAndMemberId(eventId, memberId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            event.participate();
            participantRepository.save(new FirstComeEventParticipant(event, memberId));

            return new FirstComeEventParticipateResponse(
                    event.getId(),
                    memberId,
                    event.getRemainingCount()
            );
        });
    }
}
