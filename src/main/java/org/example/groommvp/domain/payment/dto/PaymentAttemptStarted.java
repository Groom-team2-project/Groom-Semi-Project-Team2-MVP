package org.example.groommvp.domain.payment.dto;

/**
 * 결제 시도 시작 결과.
 *
 * <p>승인 요청은 트랜잭션 밖에서 이루어지므로 주문을 다시 조회할 수 없다. 승인에 필요한
 * <b>서버가 보관한 주문 금액</b>을 시작 단계에서 함께 넘겨, 클라이언트가 보낸 금액을
 * 신뢰하지 않도록 한다. (금액 위변조 방지)
 *
 * @param attemptId 이번 시도의 이력 ID
 * @param amount    서버가 보관한 주문 금액 — 이 값으로만 승인한다
 */
public record PaymentAttemptStarted(Long attemptId, Long amount) {
}
