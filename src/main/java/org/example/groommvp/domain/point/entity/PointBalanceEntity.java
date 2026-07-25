package org.example.groommvp.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

/**
 * 회원의 포인트 잔액. (테이블: point_balances)
 *
 * <p>변동 이력은 {@link PointHistoryEntity}(point_histories) 에 남기고, 이 엔티티는 현재 잔액만
 * 보관한다. 잔액을 별도 행으로 두는 이유는 <b>동시 적립/사용의 정합성</b> 때문이다 — 이력 합산으로
 * 잔액을 매번 계산하면 동시 요청에서 음수 잔액이 생길 수 있어, 이 행에 비관적 락을 걸어 직렬화한다.
 * (재고/쿠폰과 같은 패턴)
 *
 * <p>회원(member) 도메인은 파트 A 소유라 {@code MemberEntity} 에 잔액 필드를 넣지 않고, 포인트
 * 도메인 안에서 1:1 로 관리한다.
 *
 * <p><b>네이밍 컨벤션:</b> 자바 필드는 camelCase, DB 컬럼은 snake_case. (팀 컨벤션)
 */
@Entity
@Getter
@Table(name = "point_balances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointBalanceEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_balance_id")
    private Long pointBalanceId;

    /** 잔액 소유 회원 (1:1). 회원당 1개(unique). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private MemberEntity member;

    /** 현재 포인트 잔액. */
    @Column(name = "balance", nullable = false)
    private long balance;

    @Builder
    private PointBalanceEntity(MemberEntity member) {
        this.member = member;
        this.balance = 0L;
    }

    /** 회원의 초기 잔액(0) 레코드를 생성한다. */
    public static PointBalanceEntity init(MemberEntity member) {
        return PointBalanceEntity.builder().member(member).build();
    }

    /**
     * 포인트를 적립한다.
     *
     * @param amount 적립 금액 (1 이상)
     * @throws BusinessException 금액이 0 이하인 경우
     */
    public void earn(long amount) {
        validatePositive(amount);
        this.balance += amount;
    }

    /**
     * 포인트를 사용(차감)한다.
     *
     * @param amount 사용 금액 (1 이상)
     * @throws BusinessException 금액이 0 이하이거나 잔액이 부족한 경우
     */
    public void use(long amount) {
        validatePositive(amount);
        if (balance < amount) {
            throw new BusinessException(ErrorCode.POINT_NOT_ENOUGH);
        }
        this.balance -= amount;
    }

    private void validatePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_AMOUNT);
        }
    }
}
