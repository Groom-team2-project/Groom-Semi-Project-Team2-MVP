package org.example.groommvp.domain.payment.service;

import java.util.Optional;

import org.example.groommvp.domain.payment.client.TossPaymentClient;
import org.example.groommvp.domain.payment.client.TossPaymentLookupResponse;
import org.example.groommvp.domain.payment.client.TossPaymentStatus;
import org.example.groommvp.domain.payment.entity.PaymentAttempt;
import org.example.groommvp.domain.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 결제 정산의 분기 검증.
 *
 * <p>중복 완료·중복 환불이 생기지 않는지가 핵심이다. 특히 <b>조회가 실패했을 때</b>
 * 결제 없음으로 오판해 결제된 주문을 건드리지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

	@Mock private PaymentAttemptRepository paymentAttemptRepository;
	@Mock private PaymentAttemptService paymentAttemptService;
	@Mock private TossPaymentClient tossPaymentClient;
	@InjectMocks private PaymentReconciliationService paymentReconciliationService;

	private static final Long ATTEMPT_ID = 99L;
	private static final Long ORDER_ID = 1L;
	private static final String TOSS_ORDER_ID = "ORDER_1_1700000000000";

	@Test
	@DisplayName("토스에 결제 이력이 없으면 주문을 결제 대기로 되돌린다")
	void reconcile_noPaymentAtToss_revertsOrder() {
		givenUnresolvedAttempt();
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID)).willReturn(Optional.empty());

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		// 승인 요청이 닿지 않았으므로 재시도할 수 있게 되돌린다
		verify(paymentAttemptService).revert(eq(ORDER_ID), eq(ATTEMPT_ID), anyString());
		verify(paymentAttemptService, never()).settleConfirmedPayment(anyLong());
		verify(tossPaymentClient, never()).cancel(any(), any());
	}

	@Test
	@DisplayName("조회 자체가 실패하면 아무것도 건드리지 않고 다음 주기로 넘긴다")
	void reconcile_lookupFails_doesNothing() {
		givenUnresolvedAttempt();
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID))
			.willThrow(new RestClientException("toss unavailable"));

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		// 결제 없음으로 오판하면 이미 결제된 주문을 취소할 수 있다. 상태를 그대로 둬야 한다.
		verify(paymentAttemptService, never()).revert(anyLong(), anyLong(), anyString());
		verify(paymentAttemptService, never()).settleConfirmedPayment(anyLong());
		verify(paymentAttemptService, never()).markNeedsReview(anyLong(), anyString());
		verify(tossPaymentClient, never()).cancel(any(), any());
	}

	@Test
	@DisplayName("결제가 거절된 상태면 주문을 결제 대기로 되돌린다")
	void reconcile_notPaid_revertsOrder() {
		givenUnresolvedAttempt();
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID))
			.willReturn(Optional.of(lookup(TossPaymentStatus.ABORTED, 20000L)));

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		verify(paymentAttemptService).revert(eq(ORDER_ID), eq(ATTEMPT_ID), contains("ABORTED"));
		verify(tossPaymentClient, never()).cancel(any(), any());
	}

	@Test
	@DisplayName("결제 성공이고 예약이 남아 있으면 주문을 완료 처리한다 (환불하지 않는다)")
	void reconcile_paidAndRecoverable_completes() {
		givenUnresolvedAttempt();
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID))
			.willReturn(Optional.of(lookup(TossPaymentStatus.DONE, 20000L)));
		given(paymentAttemptService.settleConfirmedPayment(ATTEMPT_ID))
			.willReturn(PaymentReconcileAction.COMPLETED);

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		verify(tossPaymentClient, never()).cancel(any(), any());
		verify(paymentAttemptService, never()).markNeedsReview(anyLong(), anyString());
	}

	@Test
	@DisplayName("결제 성공이지만 주문이 이미 종료됐으면 자동 환불하고 확인 대상으로 남긴다")
	void reconcile_paidButOrderClosed_refunds() {
		givenUnresolvedAttempt();
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID))
			.willReturn(Optional.of(lookup(TossPaymentStatus.DONE, 20000L)));
		given(paymentAttemptService.settleConfirmedPayment(ATTEMPT_ID))
			.willReturn(PaymentReconcileAction.NEEDS_REFUND);

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		verify(tossPaymentClient).cancel(eq("test_pk_123"), anyString());
		verify(paymentAttemptService).markNeedsReview(eq(ATTEMPT_ID), contains("자동 환불 완료"));
	}

	@Test
	@DisplayName("자동 환불까지 실패하면 재시도하지 않고 운영 확인 대상으로 남긴다")
	void reconcile_refundFails_marksNeedsReview() {
		givenUnresolvedAttempt();
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID))
			.willReturn(Optional.of(lookup(TossPaymentStatus.DONE, 20000L)));
		given(paymentAttemptService.settleConfirmedPayment(ATTEMPT_ID))
			.willReturn(PaymentReconcileAction.NEEDS_REFUND);
		org.mockito.Mockito.doThrow(new RestClientException("refund failed"))
			.when(tossPaymentClient).cancel(anyString(), anyString());

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		// 자동 재시도를 반복하면 중복 환불 위험이 있으므로 사람이 판단하게 한다
		verify(paymentAttemptService).markNeedsReview(eq(ATTEMPT_ID), contains("자동 환불 실패"));
	}

	@Test
	@DisplayName("결제 금액이 주문 금액과 다르면 완료하지 않고 환불한다")
	void reconcile_amountMismatch_refunds() {
		givenUnresolvedAttempt();
		// 주문은 20000원인데 19000원이 결제된 상황
		given(tossPaymentClient.findByOrderId(TOSS_ORDER_ID))
			.willReturn(Optional.of(lookup(TossPaymentStatus.DONE, 19000L)));

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		verify(paymentAttemptService, never()).settleConfirmedPayment(anyLong());
		verify(tossPaymentClient).cancel(eq("test_pk_123"), anyString());
		verify(paymentAttemptService).markNeedsReview(eq(ATTEMPT_ID), contains("금액 불일치"));
	}

	@Test
	@DisplayName("이미 정리된 시도는 토스에 조회하지 않는다")
	void reconcile_alreadyResolved_skips() {
		PaymentAttempt attempt = attempt();
		attempt.succeed();
		given(paymentAttemptRepository.findById(ATTEMPT_ID)).willReturn(Optional.of(attempt));

		paymentReconciliationService.reconcile(ATTEMPT_ID);

		verify(tossPaymentClient, never()).findByOrderId(anyString());
	}

	private void givenUnresolvedAttempt() {
		given(paymentAttemptRepository.findById(ATTEMPT_ID)).willReturn(Optional.of(attempt()));
	}

	private PaymentAttempt attempt() {
		PaymentAttempt attempt =
			new PaymentAttempt(ORDER_ID, TOSS_ORDER_ID, "test_pk_123", 20000L, "CARD");
		ReflectionTestUtils.setField(attempt, "id", ATTEMPT_ID);
		return attempt;
	}

	private TossPaymentLookupResponse lookup(TossPaymentStatus status, Long amount) {
		return new TossPaymentLookupResponse(
			"test_pk_123", TOSS_ORDER_ID, status, amount, "카드", "2026-07-29T10:00:00+09:00");
	}
}
