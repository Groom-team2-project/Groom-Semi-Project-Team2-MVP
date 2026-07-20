package org.example.groommvp.domain.payment.service;

import org.example.groommvp.domain.order.entity.Order;
import org.example.groommvp.domain.order.repository.OrderRepository;
import org.example.groommvp.domain.payment.client.TossPaymentClient;
import org.example.groommvp.domain.payment.dto.PaymentRequest;
import org.example.groommvp.domain.payment.dto.PaymentResponse;
import org.example.groommvp.domain.payment.entity.Payment;
import org.example.groommvp.domain.payment.entity.PaymentStatus;
import org.example.groommvp.domain.payment.repository.PaymentRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

	@Mock private OrderRepository orderRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private TossPaymentClient tossPaymentClient;   // 외부 호출은 Mock
	@InjectMocks private PaymentService paymentService;

	@Test
	@DisplayName("결제 성공 시 토스 승인 후 상태가 PAID가 된다")
	void pay_success() {
		// given
		Long orderId = 1L;
		Order order = order(orderId, 20000L);

		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(false);
		given(paymentRepository.saveAndFlush(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		PaymentResponse response = paymentService.pay(orderId, new PaymentRequest("test_pk_123", "CARD"));

		// then
		assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(response.paidAt()).isNotNull();
		verify(tossPaymentClient).confirm("test_pk_123", "1", 20000L);  // 서버 금액으로 승인 요청했는지
		verify(paymentRepository).saveAndFlush(any(Payment.class));
	}

	@Test
	@DisplayName("이미 결제된 주문이면 토스 호출 없이 PAYMENT_ALREADY_EXISTS 예외가 발생한다")
	void pay_alreadyExists() {
		// given
		Long orderId = 1L;
		Order order = order(orderId, 20000L);
		given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
		given(paymentRepository.existsByOrder(order)).willReturn(true);

		// when & then
		assertThatThrownBy(() -> paymentService.pay(orderId, new PaymentRequest("test_pk_123", "CARD")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);

		// 사전 체크에서 막혀 토스 승인은 호출되지 않아야 한다
		verify(tossPaymentClient, never()).confirm(any(), any(), anyLong());
	}

	private Order order(Long id, Long totalPrice) {
		Order o = new Order(totalPrice);
		org.springframework.test.util.ReflectionTestUtils.setField(o, "id", id);
		return o;
	}
}
