package org.example.groommvp.domain.cart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 설정. (파트 E)
 *
 * <p>현재 유일한 스케줄 작업은 미결제 주문의 예약 재고 회수
 * ({@code ReservationExpiryScheduler}) 다. {@code @EnableScheduling} 은 애플리케이션 전역에
 * 적용되므로, 다른 파트가 스케줄 작업을 추가하게 되면 이 설정은
 * {@code global/config} 로 옮기는 것이 맞다. (캐시 설정 {@link CartCacheConfig} 도 같은 상황)
 */
@Configuration
@EnableScheduling
public class CartSchedulingConfig {
}
