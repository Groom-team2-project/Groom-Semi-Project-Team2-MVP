package org.example.groommvp.domain.payment.event;

import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.service.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

	private final OrderRepository orderRepository;
	private final MemberRepository memberRepository;
	private final EmailService emailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPaymentCompleted(PaymentCompletedEvent event) {
		log.info(
			"결제 완료 이벤트 수신 - orderId={}, paymentId={}, amount={}",
			event.orderId(), event.paymentId(), event.amount()
		);

		Order order = orderRepository.findById(event.orderId()).orElse(null);
		if (order == null || order.getMemberId() == null) {
			log.warn("결제 완료 메일 스킵 - 회원 정보 없음, orderId={}", event.orderId());
			return;
		}

		memberRepository.findById(order.getMemberId()).ifPresent(member ->
			emailService.sendPaymentCompleted(member.getEmail(), event.orderId(), event.amount())
		);
	}
}
