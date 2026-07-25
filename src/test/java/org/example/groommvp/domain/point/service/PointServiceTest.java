package org.example.groommvp.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.point.entity.PointBalanceEntity;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;
import org.example.groommvp.domain.point.entity.PointHistoryType;
import org.example.groommvp.domain.point.repository.PointBalanceRepository;
import org.example.groommvp.domain.point.repository.PointHistoryRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 포인트 서비스 단위 테스트. (적립/사용 시 이력 기록 · 잔액 부족)
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private PointBalanceRepository pointBalanceRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private PointService pointService;

    private static MemberEntity member() {
        MemberEntity member = MemberEntity.createKakaoMember("kakao-1", "u@example.com", "회원");
        ReflectionTestUtils.setField(member, "memberId", MEMBER_ID);
        return member;
    }

    private static PointBalanceEntity balanceWith(long initial) {
        PointBalanceEntity balance = PointBalanceEntity.init(member());
        if (initial > 0) {
            balance.earn(initial);
        }
        return balance;
    }

    @Test
    @DisplayName("적립하면 잔액이 늘고 EARN 이력이 남는다")
    void earn_increasesBalanceAndRecordsHistory() {
        PointBalanceEntity balance = balanceWith(0);
        given(pointBalanceRepository.findByMemberIdWithPessimisticLock(MEMBER_ID))
                .willReturn(Optional.of(balance));

        long result = pointService.earn(MEMBER_ID, 500, 42L);

        assertThat(result).isEqualTo(500);
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(captor.getValue().getAmount()).isEqualTo(500);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(500);
        assertThat(captor.getValue().getOrderId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("사용하면 잔액이 줄고 USE 이력이 남는다")
    void use_decreasesBalanceAndRecordsHistory() {
        PointBalanceEntity balance = balanceWith(1000);
        given(pointBalanceRepository.findByMemberIdWithPessimisticLock(MEMBER_ID))
                .willReturn(Optional.of(balance));

        long result = pointService.use(MEMBER_ID, 300, 42L);

        assertThat(result).isEqualTo(700);
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointHistoryType.USE);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(700);
    }

    @Test
    @DisplayName("잔액이 부족하면 사용에 실패하고 이력을 남기지 않는다")
    void use_throwsWhenNotEnough() {
        PointBalanceEntity balance = balanceWith(100);
        given(pointBalanceRepository.findByMemberIdWithPessimisticLock(MEMBER_ID))
                .willReturn(Optional.of(balance));

        assertThatThrownBy(() -> pointService.use(MEMBER_ID, 500, 42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_NOT_ENOUGH);

        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용 취소하면 잔액이 복구되고 CANCEL 이력이 남는다")
    void cancelUse_restoresBalance() {
        PointBalanceEntity balance = balanceWith(200);
        given(pointBalanceRepository.findByMemberIdWithPessimisticLock(MEMBER_ID))
                .willReturn(Optional.of(balance));

        long result = pointService.cancelUse(MEMBER_ID, 300, 42L);

        assertThat(result).isEqualTo(500);
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointHistoryType.CANCEL);
    }

    @Test
    @DisplayName("잔액 레코드가 없으면 회원을 찾아 새로 만든 뒤 적립한다")
    void earn_createsBalanceWhenAbsent() {
        given(pointBalanceRepository.findByMemberIdWithPessimisticLock(MEMBER_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(balanceWith(0)));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member()));

        pointService.earn(MEMBER_ID, 500, null);

        verify(pointBalanceRepository).save(any(PointBalanceEntity.class));
    }

    @Test
    @DisplayName("잔액 레코드가 없으면 잔액 0을 조회한다")
    void getBalance_returnsZeroWhenAbsent() {
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThat(pointService.getBalance(MEMBER_ID).balance()).isZero();
    }
}
