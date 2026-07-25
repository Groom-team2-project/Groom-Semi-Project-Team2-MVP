package org.example.groommvp.domain.point.service;

import java.util.List;
import java.util.Optional;
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
     * 주문에서 사용했던 포인트를 되돌린다. (주문 취소/결제 실패 시 호출)
     *
     * <p><b>멱등성:</b> 호출자가 준 금액을 그대로 복구하지 않는다. 그러면 취소 이벤트가 재전송되거나
     * 금액이 원 사용액과 다를 때 잔액이 부풀려진다. 대신
     * <ol>
     *   <li>해당 주문에 이미 CANCEL 이력이 있으면 <b>아무것도 하지 않고</b> 현재 잔액을 반환한다.</li>
     *   <li>없으면 그 주문의 <b>USE 이력에서 실제 사용액</b>을 합산해 그만큼만 복구한다.</li>
     * </ol>
     * 잔액 행 락으로 같은 회원의 취소가 직렬화되고, {@code (member_id, order_id, type)} 유니크
     * 제약이 최종 방어선이므로 CANCEL 은 주문당 한 번만 적재된다.
     *
     * @param orderId 취소 대상 주문 ID (필수)
     * @return 복구 후 잔액
     */
    @Transactional
    public long cancelUse(Long memberId, Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        PointBalanceEntity balance = getOrCreateBalanceWithLock(memberId);

        // 이미 복구된 주문이면 멱등 처리 (재전송된 취소 이벤트).
        if (pointHistoryRepository.existsByMember_MemberIdAndOrderIdAndType(
                memberId, orderId, PointHistoryType.CANCEL)) {
            return balance.getBalance();
        }

        // 그 주문에서 실제 사용한 포인트만 복구한다.
        long usedAmount = pointHistoryRepository
                .findByMember_MemberIdAndOrderIdAndType(memberId, orderId, PointHistoryType.USE)
                .stream()
                .mapToLong(PointHistoryEntity::getAmount)
                .sum();
        if (usedAmount <= 0) {
            return balance.getBalance();
        }

        balance.earn(usedAmount);
        recordHistory(balance.getMember(), PointHistoryType.CANCEL, usedAmount, balance.getBalance(), orderId);
        return balance.getBalance();
    }

    /**
     * 잔액 행을 락을 걸어 조회하고, 없으면 생성한다.
     *
     * <p><b>최초 생성 경합 방지:</b> 잔액 락({@code findByMemberIdWithPessimisticLock})은 행이
     * 있을 때만 잠그므로, 잔액이 아직 없는 상태에서 동시 요청이 각자 {@code save(init)} 하면
     * {@code member_id} 유니크 제약으로 한 요청이 롤백된다. 이를 막기 위해 잔액이 없을 때는
     * <b>회원 행을 먼저 잠가</b> 최초 생성 구간을 직렬화한다.
     *
     * <ol>
     *   <li>잔액 행이 있으면 그 행을 잠그고 반환 (빠른 경로, 회원 락 불필요).</li>
     *   <li>없으면 회원 행을 잠근다 → 동시 최초 생성이 직렬화된다.</li>
     *   <li>회원 락 안에서 잔액을 <b>현재 읽기(FOR UPDATE)</b>로 다시 확인한다.
     *       앞선 트랜잭션이 이미 만들었으면 그 행을 쓰고, 아니면 새로 만든다.</li>
     * </ol>
     */
    private PointBalanceEntity getOrCreateBalanceWithLock(Long memberId) {
        Optional<PointBalanceEntity> locked = pointBalanceRepository.findByMemberIdWithPessimisticLock(memberId);
        if (locked.isPresent()) {
            return locked.get();
        }

        MemberEntity member = memberRepository.findByIdWithPessimisticLock(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return pointBalanceRepository.findByMemberIdWithPessimisticLock(memberId)
                .orElseGet(() -> pointBalanceRepository.save(PointBalanceEntity.init(member)));
    }

    private void recordHistory(MemberEntity member, PointHistoryType type, long amount,
                               long balanceAfter, Long orderId) {
        pointHistoryRepository.save(
                PointHistoryEntity.of(member, type, amount, balanceAfter, orderId));
    }
}
