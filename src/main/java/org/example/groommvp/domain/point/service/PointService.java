package org.example.groommvp.domain.point.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.domain.point.dto.PointBalanceResponse;
import org.example.groommvp.domain.point.dto.PointHistoryResponse;
import org.example.groommvp.domain.point.entity.PointBalanceEntity;
import org.example.groommvp.domain.point.entity.PointHistoryEntity;
import org.example.groommvp.domain.point.entity.PointHistoryType;
import org.example.groommvp.domain.point.repository.PointBalanceRepository;
import org.example.groommvp.domain.point.repository.PointHistoryRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 적립/사용/조회 서비스.
 *
 * <p><b>적립·사용은 진입점만 제공한다.</b> 포인트는 회원이 직접 늘리는 값이 아니라 구매 확정 시
 * 적립되고 결제 시 차감되는 값이므로, 공개 엔드포인트가 아니라 주문/결제 흐름(파트 C/D)에서
 * 호출하는 메서드로 둔다. (쿠폰 {@code useCoupon} 과 같은 방식) 아직 주문 흐름에는 연결하지 않았다.
 *
 * <p><b>동시성:</b> 잔액 증감은 잔액 행 비관적 락으로 직렬화한다. 조회는 락 없이 읽는다.
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointBalanceRepository pointBalanceRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final MemberRepository memberRepository;

    /** 내 포인트 잔액 조회. 잔액 레코드가 없으면 0 으로 본다. */
    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(Long memberId) {
        long balance = pointBalanceRepository.findByMember_MemberId(memberId)
                .map(PointBalanceEntity::getBalance)
                .orElse(0L);
        return PointBalanceResponse.of(memberId, balance);
    }

    /** 내 포인트 변동 이력 조회. (최신순) */
    @Transactional(readOnly = true)
    public List<PointHistoryResponse> getHistories(Long memberId) {
        return pointHistoryRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(PointHistoryResponse::from)
                .toList();
    }

    /**
     * 포인트를 적립한다. (주문 확정 등에서 호출)
     *
     * @param orderId 적립을 유발한 주문 ID (없으면 null)
     * @return 적립 후 잔액
     */
    @Transactional
    public long earn(Long memberId, long amount, Long orderId) {
        PointBalanceEntity balance = getOrCreateBalanceWithLock(memberId);
        balance.earn(amount);
        recordHistory(balance.getMember(), PointHistoryType.EARN, amount, balance.getBalance(), orderId);
        return balance.getBalance();
    }

    /**
     * 포인트를 사용(차감)한다. (결제 시 호출)
     *
     * @param orderId 사용을 유발한 주문 ID (없으면 null)
     * @return 사용 후 잔액
     * @throws BusinessException 잔액이 부족한 경우
     */
    @Transactional
    public long use(Long memberId, long amount, Long orderId) {
        PointBalanceEntity balance = getOrCreateBalanceWithLock(memberId);
        balance.use(amount);
        recordHistory(balance.getMember(), PointHistoryType.USE, amount, balance.getBalance(), orderId);
        return balance.getBalance();
    }

    /**
     * 사용했던 포인트를 되돌린다. (주문 취소/결제 실패 시 호출)
     *
     * @return 복구 후 잔액
     */
    @Transactional
    public long cancelUse(Long memberId, long amount, Long orderId) {
        PointBalanceEntity balance = getOrCreateBalanceWithLock(memberId);
        balance.earn(amount);
        recordHistory(balance.getMember(), PointHistoryType.CANCEL, amount, balance.getBalance(), orderId);
        return balance.getBalance();
    }

    /**
     * 잔액 행을 락을 걸어 조회하고, 없으면 생성한다.
     *
     * <p>최초 적립/사용 시점에 잔액 레코드가 없을 수 있어 생성한다. 생성 직후 재조회로
     * 락을 확보해, 이후 증감이 직렬화되도록 한다.
     */
    private PointBalanceEntity getOrCreateBalanceWithLock(Long memberId) {
        return pointBalanceRepository.findByMemberIdWithPessimisticLock(memberId)
                .orElseGet(() -> {
                    MemberEntity member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                    pointBalanceRepository.save(PointBalanceEntity.init(member));
                    return pointBalanceRepository.findByMemberIdWithPessimisticLock(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                });
    }

    private void recordHistory(MemberEntity member, PointHistoryType type, long amount,
                               long balanceAfter, Long orderId) {
        pointHistoryRepository.save(
                PointHistoryEntity.of(member, type, amount, balanceAfter, orderId));
    }
}
