package org.example.groommvp.domain.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.example.groommvp.domain.payment.client.TossPaymentClient;
import org.example.groommvp.domain.payment.client.TossPaymentLookupResponse;
import org.example.groommvp.domain.payment.entity.PaymentAttempt;
import org.example.groommvp.domain.payment.entity.PaymentAttemptStatus;
import org.example.groommvp.domain.payment.repository.PaymentAttemptRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결과를 알 수 없게 된 결제 시도를 <b>토스에 물어봐서</b> 정리한다.
 *
 * <p><b>왜 필요한가:</b> 승인 요청을 보낸 뒤 응답을 받지 못하면(타임아웃, 처리 중 서버 종료)
 * 주문은 {@code PAYMENT_PROCESSING} 에 머문다. 이 상태는 만료 대상이 아니므로 그냥 두면
 * 예약 재고가 영구히 잠긴다. 반대로 무조건 풀어버리면 이미 결제된 주문을 취소하게 된다.
 * 그래서 "토스 쪽에서는 실제로 결제됐는가"를 확인한 뒤에만 처리한다.
 *
 * <p><b>중복 방지:</b> 판단은 주문을 잠근 상태에서 하고(상태를 다시 확인),
 * 외부 호출(조회·환불)은 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

	private static final String REFUND_REASON = "결제 성공 후 주문 반영 실패 (자동 환불)";

	private final PaymentAttemptRepository paymentAttemptRepository;
	private final PaymentAttemptService paymentAttemptService;
	private final TossPaymentClient tossPaymentClient;

	/**
	 * 정산 대상(결과가 반영되지 않은 채 방치된 시도)을 오래된 순으로 찾는다.
	 *
	 * @param threshold 이 시각 이전에 시작된 시도가 대상
	 */
	@Transactional(readOnly = true)
	public List<Long> findUnresolvedAttemptIds(LocalDateTime threshold, int batchSize) {
		return paymentAttemptRepository
			.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
				PaymentAttemptStatus.STARTED, threshold, PageRequest.of(0, batchSize))
			.stream()
			.map(PaymentAttempt::getId)
			.toList();
	}

	/**
	 * 시도 하나를 정산한다.
	 *
	 * <p>트랜잭션을 걸지 않는다. 토스 조회·환불이 외부 호출이기 때문이다.
	 * DB 작업은 {@link PaymentAttemptService} 의 독립 트랜잭션으로 수행한다.
	 */
	public void reconcile(Long attemptId) {
		PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId).orElse(null);
		if (attempt == null || !attempt.getStatus().isUnresolved()) {
			return;  // 그사이 정리됐다
		}

		TossPaymentLookupResponse tossPayment;
		try {
			tossPayment = tossPaymentClient.findByOrderId(attempt.getTossOrderId()).orElse(null);
		} catch (Exception e) {
			// 조회 자체가 실패했다. 결제 없음으로 오판하면 결제된 주문을 취소할 수 있으므로
			// 상태를 그대로 두고 다음 주기에 다시 시도한다.
			log.warn("토스 결제 조회 실패로 정산을 보류합니다. attemptId={}, tossOrderId={}, {}",
				attemptId, attempt.getTossOrderId(), e.getMessage());
			return;
		}

		// 토스에 결제 이력이 없다 = 승인 요청이 닿지 않았다. 주문을 결제 대기로 되돌려 재시도 가능하게.
		if (tossPayment == null) {
			log.info("토스에 결제 이력이 없어 결제 대기로 되돌립니다. attemptId={}", attemptId);
			paymentAttemptService.revert(attempt.getOrderId(), attemptId, "토스에 결제 이력 없음");
			return;
		}

		if (!tossPayment.isPaid()) {
			// 거절·만료 등으로 결제되지 않았다. 이 시도만 실패로 남기고 주문은 살려둔다.
			log.info("토스 결제가 성사되지 않아 결제 대기로 되돌립니다. attemptId={}, status={}",
				attemptId, tossPayment.status());
			paymentAttemptService.revert(attempt.getOrderId(), attemptId,
				"토스 결제 상태: " + tossPayment.status());
			return;
		}

		// 금액이 다르면 완료 처리해서는 안 된다. 환불하고 운영 확인 대상으로 남긴다.
		if (!tossPayment.matchesAmount(attempt.getAmount())) {
			log.error("결제 금액이 주문 금액과 다릅니다. attemptId={}, 결제={}, 주문={}",
				attemptId, tossPayment.totalAmount(), attempt.getAmount());
			refundAndReview(attempt, tossPayment, "결제 금액 불일치: 결제=" + tossPayment.totalAmount()
				+ ", 주문=" + attempt.getAmount());
			return;
		}

		// 결제는 성공했다. 주문을 잠그고 되돌릴 수 있는 상태인지 확인한다.
		PaymentReconcileAction action = paymentAttemptService.settleConfirmedPayment(attemptId);

		if (action == PaymentReconcileAction.NEEDS_REFUND) {
			// 예약이 이미 풀려 주문을 완료할 수 없다 → 받은 돈을 돌려준다.
			refundAndReview(attempt, tossPayment, "주문이 이미 종료되어 자동 환불");
		} else if (action == PaymentReconcileAction.COMPLETED) {
			log.info("정산으로 주문을 완료 처리했습니다. attemptId={}, orderId={}",
				attemptId, attempt.getOrderId());
		}
	}

	/**
	 * 자동 환불을 시도하고 결과를 시도 이력에 남긴다.
	 *
	 * <p>환불까지 실패하면 사람이 확인해야 한다. 자동 재시도를 반복하면 중복 환불 위험이 있으므로
	 * {@code NEEDS_REVIEW} 로 표시해 정산 대상에서 빼고 운영이 판단하게 한다.
	 */
	private void refundAndReview(PaymentAttempt attempt, TossPaymentLookupResponse tossPayment, String reason) {
		String paymentKey = tossPayment.paymentKey() != null
			? tossPayment.paymentKey() : attempt.getPaymentKey();
		try {
			tossPaymentClient.cancel(paymentKey, REFUND_REASON);
			paymentAttemptService.markNeedsReview(attempt.getId(),
				reason + " — 자동 환불 완료 (paymentKey=" + paymentKey + ")");
		} catch (Exception e) {
			paymentAttemptService.markNeedsReview(attempt.getId(),
				reason + " — 자동 환불 실패: " + e.getMessage() + " (paymentKey=" + paymentKey + ")");
		}
	}
}
