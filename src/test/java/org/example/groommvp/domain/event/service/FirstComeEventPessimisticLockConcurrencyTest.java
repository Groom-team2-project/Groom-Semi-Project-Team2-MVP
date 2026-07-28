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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "event.lock.strategy=pessimistic")
class FirstComeEventPessimisticLockConcurrencyTest {

    private static final int REQUEST_COUNT = 200;
    private static final int WORKER_COUNT = 50;

    @Autowired
    private FirstComeEventService eventService;

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
    @DisplayName("pessimistic lock keeps event count and participant rows consistent")
    void pessimisticLockKeepsParticipationConsistent() throws InterruptedException {
        FirstComeEvent event = eventRepository.save(
                new FirstComeEvent("pessimistic-lock-test", REQUEST_COUNT)
        );
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(REQUEST_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();

        try {
            for (int index = 0; index < REQUEST_COUNT; index++) {
                long memberId = index + 1L;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        eventService.participate(event.getId(), memberId);
                        successCount.incrementAndGet();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failureCount.incrementAndGet();
                        firstFailure.compareAndSet(null, exception);
                    } catch (Throwable throwable) {
                        failureCount.incrementAndGet();
                        firstFailure.compareAndSet(null, throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        if (firstFailure.get() != null) {
            throw new AssertionError("Unexpected concurrent participation failure", firstFailure.get());
        }

        FirstComeEvent savedEvent = eventRepository.findById(event.getId()).orElseThrow();

        assertThat(failureCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(REQUEST_COUNT);
        assertThat(savedEvent.getParticipatedCount()).isEqualTo(REQUEST_COUNT);
        assertThat(savedEvent.getRemainingCount()).isZero();
        assertThat(participantRepository.countByEventId(event.getId())).isEqualTo(REQUEST_COUNT);
    }
}
