package org.example.groommvp.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.point.repository.PointBalanceRepository;
import org.example.groommvp.domain.point.repository.PointHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 포인트 최초 생성 경합 테스트.
 *
 * <p>잔액 레코드가 없는 회원에게 여러 요청이 <b>동시에</b> 적립할 때, 잔액 행이 중복 생성되어
 * 유니크 제약으로 롤백되는 일 없이 모든 적립이 반영되는지 검증한다.
 * ({@code PointService#getOrCreateBalanceWithLock} 의 회원 락 직렬화)
 */
@SpringBootTest
class PointServiceConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointBalanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("잔액이 없는 회원에게 동시에 적립해도 잔액 행은 하나이고 합계가 정확하다")
    void concurrentFirstEarn_createsSingleBalanceAndSumsCorrectly() throws InterruptedException {
        MemberEntity member = memberRepository.save(
                MemberEntity.createKakaoMember("point-concurrent", "p@example.com", "포인트회원"));
        Long memberId = member.getMemberId();

        int threadCount = 20;
        long amount = 100L;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        pointService.earn(memberId, amount, null);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 1) 어떤 요청도 유니크 제약 위반 등으로 실패하지 않아야 한다.
        assertThat(failures).isEmpty();

        // 2) 잔액 행은 정확히 하나이고, 모든 적립이 합산되어야 한다.
        assertThat(pointBalanceRepository.findByMember_MemberId(memberId))
                .isPresent()
                .get()
                .satisfies(balance -> assertThat(balance.getBalance()).isEqualTo(threadCount * amount));

        // 3) 이력도 요청 수만큼 남아야 한다.
        assertThat(pointHistoryRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId))
                .hasSize(threadCount);
    }
}
