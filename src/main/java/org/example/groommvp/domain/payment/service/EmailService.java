package org.example.groommvp.domain.payment.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

	private final JavaMailSender mailSender;

	public void sendPaymentCompleted(String to, Long orderId, Long amount) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setTo(to);
			message.setSubject("[SoldOut] 결제가 완료되었습니다");
			message.setText(String.format(
				"주문 #%d 결제가 완료되었습니다.%n결제 금액: %,d원%n이용해 주셔서 감사합니다.",
				orderId, amount));
			mailSender.send(message);
			log.info("결제 완료 메일 발송 성공 - to={}, orderId={}", to, orderId);
		} catch (Exception e) {
			// 메일 발송 실패가 결제/후속 흐름을 막지 않도록 로그만 남긴다
			log.error("결제 완료 메일 발송 실패 - to={}, orderId={}", to, orderId, e);
		}
	}
}
