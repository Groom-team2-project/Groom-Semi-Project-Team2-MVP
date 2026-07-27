package org.example.groommvp.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.example.groommvp.domain.event.repository.FirstComeEventParticipantRepository;
import org.example.groommvp.domain.event.repository.FirstComeEventRepository;
import org.example.groommvp.global.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void tearDown() {
        participantRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("이벤트 수량이 100개일 때 요청 1,000개 중 정확히 100개만 성공합니다.")
    void concurrentParticipateCannotExceedLimit() throws InterruptedException {
        // given
        FirstComeEvent event = eventRepository.save(
                new FirstComeEvent("선착순 이벤트", 100)
        );

        int requestCount = 1000;
        int workerCount = 100;

        ExecutorService executorService =
                Executors.newFixedThreadPool(workerCount);

        // startLatch가 열리면 대기 중인 worker들이 함께 요청을 시작
        CountDownLatch startLatch = new CountDownLatch(1);

        // 총 1,000개의 작업이 끝날 때까지 테스트가 기다리기 위한 latch
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger expectedFailCount = new AtomicInteger();
        AtomicInteger unexpectedFailCount = new AtomicInteger();
        AtomicReference<Throwable> firstUnexpectedException = new AtomicReference<>();

        try {
            // when: 요청 작업 1,000개를 큐에 등록
            for (int i = 0; i < requestCount; i++) {
                long memberId = i + 1L;

                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        firstComeEventService.participate(
                                event.getId(),
                                memberId
                        );

                        successCount.incrementAndGet();
                    } catch (BusinessException exception) {
                        // 정원 초과 또는 락 획득 실패는 예상 가능한 비즈니스 실패
                        expectedFailCount.incrementAndGet();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        unexpectedFailCount.incrementAndGet();
                        // 첫 번째 예상 밖 오류를 저장
                        firstUnexpectedException.compareAndSet(null, exception);
                    } catch (Exception exception) {
                        // DB/Redis/코드 오류가 조용히 사라지지 않도록 별도 집계
                        unexpectedFailCount.incrementAndGet();
                        firstUnexpectedException.compareAndSet(null, exception);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // 대기 중인 worker 100개가 요청을 시작하게 함
            startLatch.countDown();

            // Redisson 락 내부에서 DB 트랜잭션이 직렬 처리되므로 충분한 시간을 부여
            assertThat(doneLatch.await(90, TimeUnit.SECONDS))
                    .as(
                            "90초 안에 요청 1,000개가 모두 끝나야 합니다. 완료=%d, 남음=%d",
                            requestCount - doneLatch.getCount(),
                            doneLatch.getCount()
                    )
                    .isTrue();
        } finally {
            executorService.shutdownNow();
        }

        Throwable unexpectedException = firstUnexpectedException.get();

        if (unexpectedException != null) {
            throw new AssertionError(
                    "선착순 요청 중 예상하지 못한 예외가 발생했습니다.",
                    unexpectedException
            );
        }

        // then
        FirstComeEvent savedEvent = eventRepository
                .findById(event.getId())
                .orElseThrow();

        assertThat(unexpectedFailCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(expectedFailCount.get()).isEqualTo(900);
        assertThat(participantRepository.count()).isEqualTo(100);
        assertThat(savedEvent.getParticipatedCount()).isEqualTo(100);
        assertThat(savedEvent.getRemainingCount()).isZero();
    }
}
