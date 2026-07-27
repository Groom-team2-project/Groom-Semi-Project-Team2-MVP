package org.example.groommvp.domain.cart.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.example.groommvp.domain.cart.service.ReservationExpiryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미결제 주문의 예약 재고를 주기적으로 회수하는 스케줄러.
 *
 * <p>결제창을 닫아버린 주문의 예약이 영구히 남아 가용 재고를 잠식하는 것을 막는다.
 * 실제 회수 로직은 {@link ReservationExpiryService} 에 있고, 여기서는 <b>언제·얼마나</b>
 * 돌릴지만 정한다.
 *
 * <p><b>주문마다 별도 트랜잭션:</b> 이 클래스는 트랜잭션 밖에서 건별로 서비스를 호출한다.
 * 한 주문의 실패가 나머지 회수를 막지 않고, 밀린 물량이 하나의 긴 트랜잭션으로 뭉치지 않는다.
 *
 * <p><b>다중 인스턴스 주의:</b> 인스턴스마다 스케줄러가 돌면 같은 주문을 동시에 집을 수 있다.
 * 주문 행 락 + 상태 재확인으로 이중 회수는 일어나지 않지만, 불필요한 락 경합이 생긴다.
 * 여러 대로 배포한다면 {@code cart.reservation.expiry.enabled=false} 로 한 대만 켜거나
 * 분산 락(파트 C의 Redisson)을 씌우는 편이 낫다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cart.reservation.expiry.enabled", havingValue = "true",
        matchIfMissing = true)
public class ReservationExpiryScheduler {

    private final ReservationExpiryService reservationExpiryService;

    /** 이 시간이 지나도록 결제되지 않은 주문의 예약을 회수한다. */
    private final Duration timeout;

    /** 한 번 실행에서 처리할 최대 주문 수. 밀린 물량이 한 번에 쏟아지지 않게 제한한다. */
    private final int batchSize;

    public ReservationExpiryScheduler(
            ReservationExpiryService reservationExpiryService,
            @Value("${cart.reservation.expiry.timeout:PT30M}") Duration timeout,
            @Value("${cart.reservation.expiry.batch-size:100}") int batchSize) {
        this.reservationExpiryService = reservationExpiryService;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${cart.reservation.expiry.interval:PT1M}")
    public void releaseExpiredReservations() {
        LocalDateTime threshold = LocalDateTime.now().minus(timeout);
        List<Long> expiredOrderIds = reservationExpiryService.findExpiredOrderIds(threshold, batchSize);
        if (expiredOrderIds.isEmpty()) {
            return;
        }

        int released = 0;
        int skipped = 0;
        int failed = 0;
        for (Long orderId : expiredOrderIds) {
            try {
                if (reservationExpiryService.releaseReservation(orderId)) {
                    released++;
                } else {
                    // 락을 잡는 사이 결제가 끝났거나 이미 취소된 주문. 정상 상황이다.
                    skipped++;
                }
            } catch (Exception e) {
                // 한 건의 실패로 나머지를 포기하지 않는다. 다음 주기에 다시 시도된다.
                failed++;
                log.warn("예약 재고 회수 실패. 다음 주기에 재시도합니다. orderId={}", orderId, e);
            }
        }

        log.info("예약 재고 회수 완료. 대상={}, 회수={}, 건너뜀={}, 실패={}, 기준시각={}",
                expiredOrderIds.size(), released, skipped, failed, threshold);
        if (expiredOrderIds.size() == batchSize) {
            // 상한에 걸렸다는 것은 아직 남아 있다는 뜻이다. 조용히 잘리지 않도록 남긴다.
            log.info("이번 주기 상한({})에 도달했습니다. 남은 대상은 다음 주기에 처리됩니다.", batchSize);
        }
    }
}
