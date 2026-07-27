package org.example.groommvp.domain.payment.event;

import java.util.Optional;

import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

	@Mock private OrderRepository orderRepository;
	@Mock private MemberRepository memberRepository;
	@Mock private EmailService emailService;
	@InjectMocks private PaymentEventListener listener;

	@Test
	@DisplayName("결제 완료 이벤트를 받으면 회원 이메일로 발송한다")
	void onPaymentCompleted_sendsEmail() {
		// given
		Long orderId = 1L;
		Long memberId = 5L;
		PaymentCompletedEvent event = new PaymentCompletedEvent(orderId, 10L, 20000L);
		Order order = Order.pendingPayment(memberId, 20000L);
		MemberEntity member = MemberEntity.createKakaoMember("kakao-1", "buyer@example.com", "구매자");

		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

		// when
		listener.onPaymentCompleted(event);

		// then
		verify(emailService).sendPaymentCompleted("buyer@example.com", orderId, 20000L);
	}

	@Test
	@DisplayName("주문에 회원 정보가 없으면 메일을 발송하지 않는다")
	void onPaymentCompleted_noMember_skip() {
		// given
		Long orderId = 1L;
		PaymentCompletedEvent event = new PaymentCompletedEvent(orderId, 10L, 20000L);
		Order order = Order.pendingPayment(null, 20000L);  // memberId 없음
		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

		// when
		listener.onPaymentCompleted(event);

		// then
		verify(emailService, never()).sendPaymentCompleted(any(), any(), any());
	}
}
