package org.example.groommvp.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.example.groommvp.domain.event.repository.FirstComeEventParticipantRepository;
import org.example.groommvp.domain.event.repository.FirstComeEventRepository;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 선착순 이벤트 동시성 테스트.
 *
 * 1,000명이 동시에 같은 이벤트에 참여해도 Redisson 분산락으로 제한 수량 100명까지만 성공하는지 검증
 * CountDownLatch로 모든 요청의 출발 시점을 최대한 맞춤.
 */
@SpringBootTest
class FirstComeEventServiceConcurrencyTest {
    @Autowired
    private FirstComeEventService firstComeEventService;

    @Autowired
    private FirstComeEventRepository eventRepository;

    @Autowired
    private FirstComeEventParticipantRepository participantRepository;

    @AfterEach
    void tearDown() {
        participantRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("이벤트 수량이 100개일 때 1,000명이 동시에 참여해도 정확히 100명만 성공합니다.")
    void concurrentParticipateCannotExceedLimit() throws InterruptedException {
        // given
        FirstComeEvent event = eventRepository.save(new FirstComeEvent("선착순 이벤트", 100));

        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1L;

            executorService.submit(() -> {
                readyLatch.countDown();

                try {
                    startLatch.await();
                    firstComeEventService.participate(event.getId(), memberId);
                    successCount.incrementAndGet();
                } catch (BusinessException exception) {
                    failCount.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(doneLatch.await(20, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();

        // then
        FirstComeEvent savedEvent = eventRepository.findById(event.getId()).orElseThrow();

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(900);
        assertThat(participantRepository.count()).isEqualTo(100);
        assertThat(savedEvent.getParticipatedCount()).isEqualTo(100);
        assertThat(savedEvent.getRemainingCount()).isZero();
    }
}
