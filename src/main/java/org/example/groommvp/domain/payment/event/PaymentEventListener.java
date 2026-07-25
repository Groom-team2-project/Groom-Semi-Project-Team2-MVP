package org.example.groommvp.domain.payment.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentEventListener {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPaymentCompleted(PaymentCompletedEvent event) {
		log.info(
			"결제 완료 이벤트 수신 - orderId={}, paymentId={}, amount={}",
			event.orderId(), event.paymentId(), event.amount()
		);

		// TODO: 여기서 결제 완료 이메일 발송 할 예정
	}
}
