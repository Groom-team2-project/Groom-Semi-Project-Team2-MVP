package org.example.groommvp.domain.payment.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.example.groommvp.domain.payment.service.PaymentReconciliationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 결과가 반영되지 않은 결제 시도를 주기적으로 정산한다.
 *
 * <p>승인 요청을 보낸 뒤 응답을 받지 못하면 주문이 {@code PAYMENT_PROCESSING} 에 머문다.
 * 이 상태는 예약 만료 대상이 아니므로 방치하면 재고가 영구히 잠긴다. 이 스케줄러가
 * 토스에 실제 결제 여부를 물어 "완료 재시도" 또는 "자동 환불"로 마무리한다.
 *
 * <p><b>타임아웃을 짧게 두면 안 된다.</b> 승인이 정상 진행 중인 시도를 정산이 먼저 집으면
 * 불필요한 경합이 생긴다. 토스 승인이 끝날 시간(기본 5분)을 충분히 준 뒤 대상으로 삼는다.
 *
 * <p><b>시간 관계:</b> 정산 타임아웃(5분)은 예약 만료(30분)보다 짧아야 한다. 그래야 결제가
 * 성공했는데 반영되지 않은 주문을 예약이 풀리기 전에 완료 처리할 수 있다.
 *
 * <p><b>다중 인스턴스 주의:</b> 여러 대에서 돌면 같은 시도를 동시에 집을 수 있다. 주문 행 락과
 * 상태 재확인으로 중복 완료·중복 환불은 막지만, 한 대만 켜는 편이 낫다.
 * ({@code payment.reconciliation.enabled=false})
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true",
	matchIfMissing = true)
public class PaymentReconciliationScheduler {

	private final PaymentReconciliationService paymentReconciliationService;

	/** 승인 요청 후 이 시간이 지나도 결과가 반영되지 않은 시도를 정산 대상으로 본다. */
	private final Duration timeout;

	/** 한 번 실행에서 처리할 최대 건수. 밀린 물량이 한꺼번에 쏟아지지 않게 제한한다. */
	private final int batchSize;

	public PaymentReconciliationScheduler(
		PaymentReconciliationService paymentReconciliationService,
		@Value("${payment.reconciliation.timeout:PT5M}") Duration timeout,
		@Value("${payment.reconciliation.batch-size:50}") int batchSize) {
		this.paymentReconciliationService = paymentReconciliationService;
		this.timeout = timeout;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${payment.reconciliation.interval:PT1M}")
	public void reconcileUnresolvedAttempts() {
		LocalDateTime threshold = LocalDateTime.now().minus(timeout);
		List<Long> attemptIds =
			paymentReconciliationService.findUnresolvedAttemptIds(threshold, batchSize);
		if (attemptIds.isEmpty()) {
			return;
		}

		log.info("결제 정산 대상 {}건을 처리합니다.", attemptIds.size());
		int failed = 0;
		for (Long attemptId : attemptIds) {
			try {
				paymentReconciliationService.reconcile(attemptId);
			} catch (Exception e) {
				// 한 건의 실패로 나머지를 포기하지 않는다. 다음 주기에 다시 시도된다.
				failed++;
				log.error("결제 정산에 실패했습니다. attemptId={}", attemptId, e);
			}
		}
		if (failed > 0) {
			log.warn("결제 정산 {}건 중 {}건이 실패했습니다.", attemptIds.size(), failed);
		}
	}
}
