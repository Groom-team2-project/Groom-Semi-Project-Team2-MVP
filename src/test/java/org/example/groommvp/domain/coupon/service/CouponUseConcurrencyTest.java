package org.example.groommvp.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.DiscountType;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.example.groommvp.domain.coupon.repository.CouponRepository;
import org.example.groommvp.domain.coupon.repository.MemberCouponRepository;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 쿠폰 사용 동시성 테스트.
 *
 * <p>같은 보유 쿠폰에 <b>동시에</b> 사용 요청이 들어와도 딱 한 번만 사용되는지 검증한다.
 * 락이 없으면 두 요청이 모두 "미사용"으로 읽어 각자 다른 주문에 할인을 적용하고, DB 에는
 * 사용 1건만 남아 쿠폰 한 장으로 여러 건이 할인된다.
 * ({@code CouponService#useCoupon} 의 보유 쿠폰 행 비관적 락)
 */
@SpringBootTest
class CouponUseConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 쿠폰에 동시에 사용 요청이 와도 단 한 번만 사용된다")
    void concurrentUse_appliesDiscountExactlyOnce() throws InterruptedException {
        MemberEntity member = memberRepository.save(
                MemberEntity.createKakaoMember("coupon-use", "cu@example.com", "쿠폰회원"));
        LocalDateTime now = LocalDateTime.now();
        CouponEntity coupon = couponRepository.save(CouponEntity.builder()
                .couponName("정액 2000원")
                .discountType(DiscountType.FIXED)
                .discountValue(2000)
                .minOrderAmount(0)
                .totalQuantity(100)
                .issueStartAt(now.minusDays(1))
                .issueEndAt(now.plusDays(1))
                .validDays(30)
                .build());
        MemberCouponEntity issued = memberCouponRepository.save(
                MemberCouponEntity.issue(member, coupon, now));

        Long memberId = member.getMemberId();
        Long memberCouponId = issued.getMemberCouponId();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                long orderId = 1000L + i; // 요청마다 다른 주문
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        couponService.useCoupon(memberId, memberCouponId, 10_000L, orderId);
                        successCount.incrementAndGet();
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures).hasSize(threadCount - 1);

        List<MemberCouponEntity> after = memberCouponRepository.findAll();
        assertThat(after).singleElement().satisfies(mc -> {
            assertThat(mc.isUsed()).isTrue();
            assertThat(mc.getUsedOrderId()).isNotNull();
        });
    }
}
