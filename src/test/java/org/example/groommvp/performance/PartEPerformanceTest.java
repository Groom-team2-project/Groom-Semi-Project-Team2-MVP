package org.example.groommvp.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.groommvp.domain.cart.dto.CartItemAddRequest;
import org.example.groommvp.domain.cart.repository.CartItemRepository;
import org.example.groommvp.domain.cart.repository.CartRepository;
import org.example.groommvp.domain.cart.service.CartOrderService;
import org.example.groommvp.domain.cart.service.CartService;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.example.groommvp.domain.coupon.entity.DiscountType;
import org.example.groommvp.domain.coupon.entity.MemberCouponEntity;
import org.example.groommvp.domain.coupon.repository.CouponRepository;
import org.example.groommvp.domain.coupon.repository.MemberCouponRepository;
import org.example.groommvp.domain.coupon.service.CouponService;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.order.repository.OrderItemRepository;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.point.repository.PointBalanceRepository;
import org.example.groommvp.domain.point.repository.PointHistoryRepository;
import org.example.groommvp.domain.point.service.PointService;
import org.example.groommvp.domain.product.entity.ProductEntity;
import org.example.groommvp.domain.product.repository.ProductRepository;
import org.example.groommvp.domain.stock.entity.StockEntity;
import org.example.groommvp.domain.stock.repository.StockHistoryRepository;
import org.example.groommvp.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 파트 E(장바구니·쿠폰·포인트) 성능 측정.
 *
 * <p>적대적 리뷰에서 추가한 <b>비관적 락</b>(장바구니 행 / 보유 쿠폰 행)과 <b>flush</b> 가
 * 처리량에 얼마나 영향을 주는지, 그리고 장바구니 조회 캐시가 실제로 효과가 있는지 측정한다.
 *
 * <p><b>이것은 회귀 테스트가 아니라 계측이다.</b> 절대 수치는 실행 환경(H2/MySQL, CPU)에 크게
 * 의존하므로 임계값 단언은 최소로 두고, 결과는 표준 출력으로 남겨 사람이 비교하도록 한다.
 * 의미 있는 비교는 "같은 장비에서 수정 전/후" 또는 "캐시 히트 vs 미스" 처럼 상대값이다.
 *
 * <p>실제 MySQL/Redis 로 재려면:
 * <pre>gradlew test --tests "*PartEPerformanceTest" -Dspring.profiles.active=mysql</pre>
 */
@SpringBootTest
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class PartEPerformanceTest {

    /** 동시 실행 스레드 수. 실제 서비스의 동시 요청을 흉내낸다. */
    private static final int THREADS = 32;
    /** 측정 구간에서 스레드당 반복 횟수. */
    private static final int ITERATIONS_PER_THREAD = 20;
    /** 측정 전 JIT/커넥션풀/하이버네이트 캐시를 데우는 횟수. */
    private static final int WARMUP_ITERATIONS = 50;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartOrderService cartOrderService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private PointService pointService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        pointHistoryRepository.deleteAllInBatch();
        pointBalanceRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // ---------------------------------------------------------------- 장바구니

    @Test
    @Order(1)
    @DisplayName("[성능] 장바구니 조회 — 캐시 히트 vs 미스")
    void measure_getMyCart_cacheHitVsMiss() {
        Long memberId = newMember("perf-cart-read").getMemberId();
        Long productId = newProduct("상품", 10_000).getProductId();
        cartService.addItem(memberId, new CartItemAddRequest(productId, 1));

        // 캐시 미스: 매번 무효화해 DB 조회를 강제한다.
        Result miss = measure("장바구니 조회 (캐시 미스)", () -> {
            cartService.clearCart(memberId); // @CacheEvict — 다음 조회는 반드시 DB
            cartService.getMyCart(memberId);
            return null;
        });

        cartService.addItem(memberId, new CartItemAddRequest(productId, 1));
        cartService.getMyCart(memberId); // 캐시 적재
        Result hit = measure("장바구니 조회 (캐시 히트)", () -> cartService.getMyCart(memberId));

        report(miss, hit);
        assertThat(hit.opsPerSecond()).isPositive();
    }

    @Test
    @Order(2)
    @DisplayName("[성능] 장바구니 담기 — 회원별 독립(경합 없음) vs 한 회원에 집중(락 경합)")
    void measure_addItem_lockContention() {
        Long productId = newProduct("상품", 10_000).getProductId();

        // 경합 없음: 스레드마다 다른 회원 → 장바구니 락이 서로 겹치지 않는다.
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(newMember("perf-add-" + i).getMemberId());
        }
        AtomicInteger cursor = new AtomicInteger();
        Result independent = measurePerThread("장바구니 담기 (회원별 독립)", threadIndex -> {
            Long memberId = memberIds.get(threadIndex % memberIds.size());
            cartService.addItem(memberId, new CartItemAddRequest(productId, 1));
            cursor.incrementAndGet();
            return null;
        });

        // 최악: 모든 스레드가 같은 회원의 장바구니를 두고 경합한다.
        Long hotMemberId = newMember("perf-add-hot").getMemberId();
        Result contended = measureContended("장바구니 담기 (한 회원 집중)",
                () -> cartService.addItem(hotMemberId, new CartItemAddRequest(productId, 1)),
                () -> (long) cartService.getMyCart(hotMemberId).totalQuantity());

        report(independent, contended);

        // 락이 제 역할을 하면 집중 케이스에서도 수량 유실이 없어야 한다.
        // (워밍업분은 제외하고 측정 구간의 증가분만 본다)
        assertThat(contended.measuredDelta()).isEqualTo(contended.successCount());
    }

    @Test
    @Order(3)
    @DisplayName("[성능] 장바구니 체크아웃 — 다품목 주문 전환")
    void measure_checkout() {
        int itemsPerCart = 5;
        List<Long> productIds = new ArrayList<>();
        for (int i = 0; i < itemsPerCart; i++) {
            ProductEntity product = newProduct("상품" + i, 1_000 * (i + 1));
            stockRepository.save(new StockEntity(product, 1_000_000));
            productIds.add(product.getProductId());
        }

        int checkoutCount = THREADS * 4;
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < checkoutCount; i++) {
            Long memberId = newMember("perf-checkout-" + i).getMemberId();
            // 절반은 담는 순서를 뒤집어 교차 락 상황을 만든다.
            List<Long> order = new ArrayList<>(productIds);
            if (i % 2 == 1) {
                java.util.Collections.reverse(order);
            }
            for (Long productId : order) {
                cartService.addItem(memberId, new CartItemAddRequest(productId, 1));
            }
            memberIds.add(memberId);
        }

        Queue<Long> queue = new ConcurrentLinkedQueue<>(memberIds);
        Result checkout = measureQueue("체크아웃 (5품목, 교차 순서)", queue, cartOrderService::checkout);

        report(checkout);
        assertThat(orderRepository.count()).isEqualTo(checkoutCount);
    }

    // -------------------------------------------------------------------- 쿠폰

    @Test
    @Order(4)
    @DisplayName("[성능] 쿠폰 발급 — 선착순 단일 행 경합")
    void measure_couponIssue() {
        CouponEntity coupon = couponRepository.save(newCoupon(THREADS * ITERATIONS_PER_THREAD));
        Long couponId = coupon.getCouponId();

        // 회원당 1장이므로 발급 요청마다 다른 회원이 필요하다.
        Queue<Long> memberIds = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < THREADS * ITERATIONS_PER_THREAD; i++) {
            memberIds.add(newMember("perf-coupon-" + i).getMemberId());
        }

        Result issue = measureQueue("쿠폰 발급 (단일 쿠폰 행 경합)", memberIds,
                memberId -> couponService.issue(memberId, couponId));

        report(issue);
        assertThat(memberCouponRepository.count()).isEqualTo(issue.successCount());
    }

    @Test
    @Order(5)
    @DisplayName("[성능] 쿠폰 사용 — 보유 쿠폰 행 락")
    void measure_couponUse() {
        CouponEntity coupon = couponRepository.save(newCoupon(THREADS * ITERATIONS_PER_THREAD + 10));
        LocalDateTime now = LocalDateTime.now();

        // 사용은 1회성이라 요청마다 별도의 보유 쿠폰이 필요하다.
        Queue<Long[]> targets = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < THREADS * ITERATIONS_PER_THREAD; i++) {
            MemberEntity member = newMember("perf-couponuse-" + i);
            MemberCouponEntity issued = memberCouponRepository.save(
                    MemberCouponEntity.issue(member, coupon, now));
            targets.add(new Long[]{member.getMemberId(), issued.getMemberCouponId()});
        }

        AtomicInteger orderSeq = new AtomicInteger(1);
        Result use = measureQueue("쿠폰 사용 (보유 쿠폰 행 락)", targets,
                target -> couponService.useCoupon(target[0], target[1], 50_000L,
                        (long) orderSeq.getAndIncrement()));

        report(use);
        assertThat(use.successCount()).isEqualTo(THREADS * ITERATIONS_PER_THREAD);
    }

    // ------------------------------------------------------------------ 포인트

    @Test
    @Order(6)
    @DisplayName("[성능] 포인트 적립 — 회원별 독립 vs 한 회원 집중(잔액 행 락)")
    void measure_pointEarn() {
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(newMember("perf-point-" + i).getMemberId());
        }
        AtomicInteger orderSeq = new AtomicInteger(1);
        Result independent = measurePerThread("포인트 적립 (회원별 독립)", threadIndex -> {
            Long memberId = memberIds.get(threadIndex % memberIds.size());
            return pointService.earn(memberId, 10, (long) orderSeq.getAndIncrement());
        });

        Long hotMemberId = newMember("perf-point-hot").getMemberId();
        AtomicInteger hotOrderSeq = new AtomicInteger(1_000_000);
        Result contended = measureContended("포인트 적립 (한 회원 집중)",
                () -> pointService.earn(hotMemberId, 10, (long) hotOrderSeq.getAndIncrement()),
                () -> pointService.getBalance(hotMemberId).balance());

        report(independent, contended);

        // 락이 제 역할을 하면 집중 케이스에서도 적립이 하나도 유실되지 않는다.
        // (워밍업분은 제외하고 측정 구간의 증가분만 본다)
        assertThat(contended.measuredDelta()).isEqualTo(10L * contended.successCount());
    }

    // ------------------------------------------------------------ 측정 인프라

    /** 모든 스레드가 같은 작업을 반복한다. (공유 자원 경합 측정용) */
    private Result measure(String name, Callable<?> task) {
        return measurePerThread(name, threadIndex -> task.call());
    }

    /**
     * 경합 측정 + 유실 검증. 워밍업이 끝난 뒤의 값을 기준점으로 잡아, 측정 구간에서 실제로
     * 반영된 증가분({@code measuredDelta})을 함께 돌려준다. 워밍업분이 섞여 들어가면
     * "성공 횟수만큼 반영됐는가" 를 검증할 수 없다.
     */
    private Result measureContended(String name, Callable<?> task, Callable<Long> counter) {
        warmUp(task::call);
        long before = call(counter);
        Result result = runConcurrently(name, THREADS * ITERATIONS_PER_THREAD,
                (threadIndex, iteration) -> task.call());
        return result.withMeasuredDelta(call(counter) - before);
    }

    private static long call(Callable<Long> counter) {
        try {
            return counter.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 스레드 인덱스를 받아 작업을 만든다. (스레드별 독립 자원 측정용) */
    private Result measurePerThread(String name, IndexedTask task) {
        warmUp(() -> task.run(0));
        return runConcurrently(name, THREADS * ITERATIONS_PER_THREAD,
                (threadIndex, iteration) -> task.run(threadIndex));
    }

    /** 큐에서 작업 대상을 하나씩 꺼내 처리한다. (요청마다 별도 자원이 필요한 경우) */
    private <T> Result measureQueue(String name, Queue<T> queue, ThrowingConsumer<T> task) {
        int total = queue.size();
        return runConcurrently(name, total, (threadIndex, iteration) -> {
            T target = queue.poll();
            if (target != null) {
                task.accept(target);
            }
            return null;
        });
    }

    private void warmUp(ThrowingRunnable task) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                task.run();
            } catch (Throwable ignored) {
                // 워밍업 실패는 측정 대상이 아니다. (수량 소진 등)
            }
        }
    }

    private Result runConcurrently(String name, int totalOperations, IndexedIterationTask task) {
        int perThread = Math.max(1, totalOperations / THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch readyLatch = new CountDownLatch(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        Queue<Long> latenciesNanos = new ConcurrentLinkedQueue<>();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        long startedAt;
        try {
            for (int t = 0; t < THREADS; t++) {
                int threadIndex = t;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        for (int i = 0; i < perThread; i++) {
                            long began = System.nanoTime();
                            try {
                                task.run(threadIndex, i);
                                latenciesNanos.add(System.nanoTime() - began);
                                success.incrementAndGet();
                            } catch (Throwable t2) {
                                failures.add(t2);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startedAt = System.nanoTime();
            startLatch.countDown();
            if (!doneLatch.await(5, TimeUnit.MINUTES)) {
                throw new IllegalStateException(name + " 측정이 5분 내에 끝나지 않았습니다. (교착 의심)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        if (!failures.isEmpty()) {
            // 실패는 처리량만큼 중요한 신호다. 어떤 예외가 몇 건인지 남겨야 "경합으로 인한
            // 정상적 거절" 과 "설계 결함" 을 구분할 수 있다.
            System.out.printf("[%s] 실패 %d건 — 유형별:%n", name, failures.size());
            failures.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            t -> rootCause(t).getClass().getSimpleName() + ": " + summarize(rootCause(t)),
                            java.util.stream.Collectors.counting()))
                    .forEach((type, count) -> System.out.printf("    %4d회  %s%n", count, type));
        }
        return new Result(name, elapsed, success.get(), failures.size(),
                latenciesNanos.stream().mapToLong(Long::longValue).sorted().toArray());
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String summarize(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            return "(no message)";
        }
        String firstLine = message.lines().findFirst().orElse(message);
        return firstLine.length() > 140 ? firstLine.substring(0, 140) + "…" : firstLine;
    }

    // -------------------------------------------------------------- 결과 출력

    private void report(Result... results) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append("=".repeat(104)).append('\n');
        sb.append(String.format("%-34s %10s %8s %8s %10s %10s %10s %10s%n",
                "시나리오", "처리량(ops/s)", "성공", "실패", "평균(ms)", "p50(ms)", "p95(ms)", "p99(ms)"));
        sb.append("-".repeat(104)).append('\n');
        for (Result r : results) {
            sb.append(String.format("%-34s %10.1f %8d %8d %10.2f %10.2f %10.2f %10.2f%n",
                    r.name(), r.opsPerSecond(), r.successCount(), r.failureCount(),
                    r.averageMillis(), r.percentileMillis(50), r.percentileMillis(95),
                    r.percentileMillis(99)));
        }
        if (results.length == 2) {
            double ratio = results[1].opsPerSecond() / results[0].opsPerSecond();
            sb.append(String.format("→ %s 대비 %s 처리량 비율: %.2f배%n",
                    results[0].name(), results[1].name(), ratio));
        }
        sb.append("=".repeat(104));
        System.out.println(sb);
    }

    private record Result(String name, Duration elapsed, int successCount, int failureCount,
                          long[] sortedLatenciesNanos, long measuredDelta) {

        Result(String name, Duration elapsed, int successCount, int failureCount,
               long[] sortedLatenciesNanos) {
            this(name, elapsed, successCount, failureCount, sortedLatenciesNanos, 0L);
        }

        Result withMeasuredDelta(long delta) {
            return new Result(name, elapsed, successCount, failureCount, sortedLatenciesNanos, delta);
        }

        double opsPerSecond() {
            double seconds = elapsed.toNanos() / 1_000_000_000.0;
            return seconds <= 0 ? 0 : successCount / seconds;
        }

        double averageMillis() {
            if (sortedLatenciesNanos.length == 0) {
                return 0;
            }
            long sum = 0;
            for (long nanos : sortedLatenciesNanos) {
                sum += nanos;
            }
            return sum / (double) sortedLatenciesNanos.length / 1_000_000.0;
        }

        double percentileMillis(int percentile) {
            if (sortedLatenciesNanos.length == 0) {
                return 0;
            }
            int index = (int) Math.ceil(percentile / 100.0 * sortedLatenciesNanos.length) - 1;
            return sortedLatenciesNanos[Math.max(0, index)] / 1_000_000.0;
        }
    }

    @FunctionalInterface
    private interface IndexedTask {
        Object run(int threadIndex) throws Exception;
    }

    @FunctionalInterface
    private interface IndexedIterationTask {
        Object run(int threadIndex, int iteration) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T target) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ------------------------------------------------------------ 픽스처 생성

    private final AtomicInteger memberSeq = new AtomicInteger();

    private MemberEntity newMember(String prefix) {
        String key = prefix + "-" + memberSeq.incrementAndGet();
        return memberRepository.save(
                MemberEntity.createKakaoMember(key, key + "@example.com", key));
    }

    private ProductEntity newProduct(String name, int price) {
        return productRepository.save(new ProductEntity(name, price));
    }

    private CouponEntity newCoupon(int totalQuantity) {
        LocalDateTime now = LocalDateTime.now();
        return CouponEntity.builder()
                .couponName("성능측정 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(2000)
                .minOrderAmount(0)
                .totalQuantity(totalQuantity)
                .issueStartAt(now.minusDays(1))
                .issueEndAt(now.plusDays(1))
                .validDays(30)
                .build();
    }
}
