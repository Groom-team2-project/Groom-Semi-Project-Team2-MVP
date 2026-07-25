package org.example.groommvp.domain.point.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 포인트 잔액 엔티티 단위 테스트. (적립/사용/잔액 부족)
 */
class PointBalanceEntityTest {

    private static PointBalanceEntity balance() {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-1", "u@example.com", "회원");
        return PointBalanceEntity.init(member);
    }

    @Test
    @DisplayName("초기 잔액은 0이다")
    void init_startsAtZero() {
        assertThat(balance().getBalance()).isZero();
    }

    @Test
    @DisplayName("적립하면 잔액이 증가한다")
    void earn_increasesBalance() {
        PointBalanceEntity balance = balance();

        balance.earn(500);
        balance.earn(300);

        assertThat(balance.getBalance()).isEqualTo(800);
    }

    @Test
    @DisplayName("사용하면 잔액이 감소한다")
    void use_decreasesBalance() {
        PointBalanceEntity balance = balance();
        balance.earn(1000);

        balance.use(400);

        assertThat(balance.getBalance()).isEqualTo(600);
    }

    @Test
    @DisplayName("잔액보다 많이 사용하면 예외가 발생하고 잔액은 그대로다")
    void use_throwsWhenNotEnough() {
        PointBalanceEntity balance = balance();
        balance.earn(300);

        assertThatThrownBy(() -> balance.use(500))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_NOT_ENOUGH);

        assertThat(balance.getBalance()).isEqualTo(300);
    }

    @Test
    @DisplayName("잔액과 정확히 같은 금액은 사용할 수 있다 (경계값)")
    void use_allowsExactBalance() {
        PointBalanceEntity balance = balance();
        balance.earn(300);

        balance.use(300);

        assertThat(balance.getBalance()).isZero();
    }

    @Test
    @DisplayName("0 이하 금액은 적립할 수 없다")
    void earn_throwsForNonPositive() {
        assertThatThrownBy(() -> balance().earn(0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_POINT_AMOUNT);
    }

    @Test
    @DisplayName("0 이하 금액은 사용할 수 없다")
    void use_throwsForNonPositive() {
        assertThatThrownBy(() -> balance().use(-100))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_POINT_AMOUNT);
    }
}
