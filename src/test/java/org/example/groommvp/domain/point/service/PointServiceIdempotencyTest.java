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
import org.example.groommvp.domain.point.entity.PointHistoryType;
import org.example.groommvp.domain.point.repository.PointBalanceRepository;
import org.example.groommvp.domain.point.repository.PointHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 포인트 적립/사용 멱등성 테스트.
 *
 * <p>결제 재시도나 이벤트 재전송으로 같은 주문에 대한 적립/사용이 <b>여러 번</b> 호출돼도
 * 잔액에는 한 번만 반영되고, {@code (member_id, order_id, type)} 유니크 제약이 500 으로
 * 새어나가지 않는지 검증한다.
 */
@SpringBootTest
class PointServiceIdempotencyTest {

    private static final Long ORDER_ID = 42L;

    @Autowired
    private PointService pointService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(
                MemberEntity.createKakaoMember("point-idem", "pi@example.com", "포인트회원")).getMemberId();
    }

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAllInBatch();
        pointBalanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 주문으로 적립을 재호출해도 한 번만 반영된다 (이벤트 재전송)")
    void earn_isIdempotentPerOrder() {
        pointService.earn(memberId, 500, ORDER_ID);
        long balance = pointService.earn(memberId, 500, ORDER_ID);

        assertThat(balance).isEqualTo(500);
        assertThat(pointService.getBalance(memberId).balance()).isEqualTo(500);
        assertThat(pointHistoryRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 주문으로 사용을 재호출해도 포인트가 두 번 빠지지 않는다 (결제 재시도)")
    void use_isIdempotentPerOrder() {
        pointService.earn(memberId, 1000, null);

        pointService.use(memberId, 300, ORDER_ID);
        long balance = pointService.use(memberId, 300, ORDER_ID);

        assertThat(balance).isEqualTo(700);
        assertThat(pointHistoryRepository
                .findByMember_MemberIdAndOrderIdAndType(memberId, ORDER_ID, PointHistoryType.USE))
                .isPresent();
    }

    @Test
    @DisplayName("주문 ID 가 없는 적립은 멱등 대상이 아니라 매번 반영된다")
    void earn_withoutOrderId_appliesEveryTime() {
        pointService.earn(memberId, 100, null);
        long balance = pointService.earn(memberId, 100, null);

        assertThat(balance).isEqualTo(200);
        assertThat(pointHistoryRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)).hasSize(2);
    }

    @Test
    @DisplayName("같은 주문으로 동시에 사용해도 딱 한 번만 차감되고 500 이 나지 않는다")
    void concurrentUse_sameOrder_deductsOnce() throws InterruptedException {
        pointService.earn(memberId, 10_000, null);

        int threadCount = 20;
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
                        pointService.use(memberId, 300, ORDER_ID);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // 잔액 행 락으로 직렬화되므로 뒤이은 요청은 멱등 검사에 걸려 조용히 통과한다.
        assertThat(failures).isEmpty();
        assertThat(pointService.getBalance(memberId).balance()).isEqualTo(9700);
        assertThat(pointHistoryRepository
                .findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .filter(h -> h.getType() == PointHistoryType.USE)
                .count()).isEqualTo(1);
    }
}
