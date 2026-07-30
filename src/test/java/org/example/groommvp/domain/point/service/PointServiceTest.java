package org.example.groommvp.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.of(balance));

        long result = pointService.earn(MEMBER_ID, 500, 42L);

        assertThat(result).isEqualTo(500);
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(captor.getValue().getAmount()).isEqualTo(500);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(500);
        assertThat(captor.getValue().getOrderId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("사용하면 잔액이 줄고 USE 이력이 남는다")
    void use_decreasesBalanceAndRecordsHistory() {
        PointBalanceEntity balance = balanceWith(1000);
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.of(balance));

        long result = pointService.use(MEMBER_ID, 300, 42L);

        assertThat(result).isEqualTo(700);
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointHistoryType.USE);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(700);
    }

    @Test
    @DisplayName("잔액이 부족하면 사용에 실패하고 이력을 남기지 않는다")
    void use_throwsWhenNotEnough() {
        PointBalanceEntity balance = balanceWith(100);
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.of(balance));

        assertThatThrownBy(() -> pointService.use(MEMBER_ID, 500, 42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_NOT_ENOUGH);

        verify(pointHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("사용 취소하면 원 USE 사용액만큼 잔액이 복구되고 CANCEL 이력이 남는다")
    void cancelUse_restoresUsedAmount() {
        PointBalanceEntity balance = balanceWith(200);
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.of(balance));
        given(pointHistoryRepository.existsByMember_MemberIdAndOrderIdAndType(MEMBER_ID, 42L, PointHistoryType.CANCEL))
                .willReturn(false);
        // 그 주문에서 실제 사용한 포인트는 300 (호출자가 주는 금액이 아니라 이력에서 도출)
        given(pointHistoryRepository.findFirstByMember_MemberIdAndOrderIdAndTypeOrderByPointHistoryIdAsc(
                MEMBER_ID, 42L, PointHistoryType.USE))
                .willReturn(Optional.of(PointHistoryEntity.of(member(), PointHistoryType.USE, 300, 0, 42L)));

        long result = pointService.cancelUse(MEMBER_ID, 42L);

        assertThat(result).isEqualTo(500);
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PointHistoryType.CANCEL);
        assertThat(captor.getValue().getAmount()).isEqualTo(300);
    }

    @Test
    @DisplayName("이미 취소된 주문이면 멱등 처리되어 잔액이 그대로다 (취소 이벤트 재전송)")
    void cancelUse_idempotentWhenAlreadyCanceled() {
        PointBalanceEntity balance = balanceWith(200);
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.of(balance));
        given(pointHistoryRepository.existsByMember_MemberIdAndOrderIdAndType(MEMBER_ID, 42L, PointHistoryType.CANCEL))
                .willReturn(true);

        long result = pointService.cancelUse(MEMBER_ID, 42L);

        assertThat(result).isEqualTo(200);
        verify(pointHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("잔액 레코드가 없으면 회원 행을 잠근 뒤 만든다 (갭 락 교착 회피)")
    void earn_createsBalanceWhenAbsent() {
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(pointBalanceRepository.saveAndFlush(any(PointBalanceEntity.class))).willReturn(balanceWith(0));

        pointService.earn(MEMBER_ID, 500, null);

        verify(memberRepository).findByIdWithPessimisticLock(MEMBER_ID);
        verify(pointBalanceRepository).saveAndFlush(any(PointBalanceEntity.class));
        // 없는 잔액 행을 FOR UPDATE 하면 갭 락이 잡혀 서로 다른 회원끼리도 교착한다.
        verify(pointBalanceRepository, never()).findByMemberIdWithPessimisticLock(any());
    }

    @Test
    @DisplayName("잔액 중복 생성이 뚫리면 재시도 가능한 예외로 변환된다 (마지막 안전망)")
    void earn_convertsUniqueConstraintViolationOnFirstCreate() {
        given(memberRepository.findByIdWithPessimisticLock(MEMBER_ID)).willReturn(Optional.of(member()));
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(pointBalanceRepository.saveAndFlush(any(PointBalanceEntity.class)))
                .willThrow(new DataIntegrityViolationException("duplicate member_id"));

        assertThatThrownBy(() -> pointService.earn(MEMBER_ID, 500, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_BUSY);
    }

    @Test
    @DisplayName("회원은 있는데 잔액 레코드가 없으면 잔액 0을 조회한다 (아직 변동이 없는 회원)")
    void getBalance_returnsZeroWhenBalanceAbsent() {
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);

        assertThat(pointService.getBalance(MEMBER_ID).balance()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 회원의 잔액 조회는 404다 (잔액 0과 구분한다)")
    void getBalance_throwsWhenMemberAbsent() {
        given(pointBalanceRepository.findByMember_MemberId(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

        assertThatThrownBy(() -> pointService.getBalance(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 이력 조회는 404다 (이력 없음과 구분한다)")
    void getHistories_throwsWhenMemberAbsent() {
        given(pointHistoryRepository.findByMember_MemberIdOrderByCreatedAtDesc(MEMBER_ID))
                .willReturn(List.of());
        given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

        assertThatThrownBy(() -> pointService.getHistories(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }
}
