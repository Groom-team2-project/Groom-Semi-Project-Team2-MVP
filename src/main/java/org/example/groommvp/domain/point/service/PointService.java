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
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * 내 포인트 잔액 조회.
     *
     * <p>잔액 레코드가 없는 것과 회원이 없는 것은 다르다. 전자는 아직 포인트 변동이 한 번도
     * 없었다는 뜻이라 <b>잔액 0</b> 이 맞지만, 후자는 존재하지 않는 회원이므로
     * {@link ErrorCode#MEMBER_NOT_FOUND} 여야 한다. 둘 다 0 으로 답하면 잘못된 회원 ID 로 호출해도
     * 200 이 나가 오류를 늦게 발견하게 된다.
     */
    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(Long memberId) {
        long balance = pointBalanceRepository.findByMember_MemberId(memberId)
                .map(PointBalanceEntity::getBalance)
                .orElseGet(() -> {
                    requireMemberExists(memberId);
                    return 0L;
                });
        return PointBalanceResponse.of(memberId, balance);
    }

    /** 내 포인트 변동 이력 조회. (최신순) */
    @Transactional(readOnly = true)
    public List<PointHistoryResponse> getHistories(Long memberId) {
        List<PointHistoryResponse> histories =
                pointHistoryRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
                        .map(PointHistoryResponse::from)
                        .toList();
        if (histories.isEmpty()) {
            // 이력이 없는 것과 회원이 없는 것을 구분한다. (잔액 조회와 같은 이유)
            requireMemberExists(memberId);
        }
        return histories;
    }

    private void requireMemberExists(Long memberId) {
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    /**
     * 포인트를 적립한다. (주문 확정 등에서 호출)
     *
     * <p><b>멱등성:</b> 같은 주문에 이미 적립했다면 아무것도 하지 않고 현재 잔액을 반환한다.
     * (구매 확정 이벤트 재전송 대비)
     *
     * @param orderId 적립을 유발한 주문 ID (없으면 null — 이때는 멱등 판정 대상이 아니다)
     * @return 적립 후 잔액
     */
    @Transactional
    public long earn(Long memberId, long amount, Long orderId) {
        PointBalanceEntity balance = getOrCreateBalanceWithLock(memberId);
        if (isAlreadyApplied(memberId, orderId, PointHistoryType.EARN)) {
            return balance.getBalance();
        }
        balance.earn(amount);
        recordHistory(balance.getMember(), PointHistoryType.EARN, amount, balance.getBalance(), orderId);
        return balance.getBalance();
    }

    /**
     * 포인트를 사용(차감)한다. (결제 시 호출)
     *
     * <p><b>멱등성:</b> 같은 주문에 이미 사용했다면 아무것도 하지 않고 현재 잔액을 반환한다.
     * 결제 재시도로 포인트가 두 번 빠지는 것을 막는다.
     *
     * @param orderId 사용을 유발한 주문 ID (없으면 null — 이때는 멱등 판정 대상이 아니다)
     * @return 사용 후 잔액
     * @throws BusinessException 잔액이 부족한 경우
     */
    @Transactional
    public long use(Long memberId, long amount, Long orderId) {
        PointBalanceEntity balance = getOrCreateBalanceWithLock(memberId);
        if (isAlreadyApplied(memberId, orderId, PointHistoryType.USE)) {
            return balance.getBalance();
        }
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

        // 그 주문에서 실제 사용한 포인트만 복구한다. (유니크 제약상 USE 이력은 최대 1건)
        long usedAmount = pointHistoryRepository
                .findByMember_MemberIdAndOrderIdAndType(memberId, orderId, PointHistoryType.USE)
                .map(PointHistoryEntity::getAmount)
                .orElse(0L);
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
     * <p><b>회원 행을 잠가 이 회원의 포인트 변동을 통째로 직렬화한다.</b> 잔액 증감은
     * read-modify-write 라서 락 없이는 동시 요청이 같은 잔액을 읽고 각자 갱신해 어긋나고,
     * 잔액 행이 아직 없으면 동시 생성이 유니크 제약으로 충돌한다. 둘 다 이 락으로 막는다.
     *
     * <p><b>왜 잔액 행이 아니라 회원 행을 잠그는가:</b> 잔액 행은 아직 없을 수 있고,
     * <b>존재하지 않는 행</b>을 {@code SELECT ... FOR UPDATE} 하면 InnoDB 는 레코드 락이 아니라
     * <b>갭 락</b>을 잡는다. 갭 락끼리는 공존하지만 뒤따르는 INSERT 의 insert-intention 락과는
     * 충돌하므로, 서로 다른 회원의 첫 적립끼리도 서로의 갭 락을 기다리며 교착한다. (32 스레드
     * 부하에서 6% 가 {@code Deadlock found} 로 실패하는 것을 확인했다. H2 에는 갭 락이 없어
     * 드러나지 않는다.) 회원 행은 <b>반드시 존재</b>하므로 레코드 락만 잡혀 이 문제가 없다.
     *
     * <p><b>순서가 중요하다.</b> 회원 락을 <b>먼저</b> 잡고 그 뒤에 잔액을 읽어야 한다.
     * REPEATABLE READ 의 읽기 뷰는 첫 <b>일반</b> 읽기에서 만들어지고 락 조회는 만들지 않으므로,
     * 락을 잡은 뒤 읽으면 앞서 락을 쥐었던 트랜잭션이 커밋한 잔액이 보인다. 멱등성 검사
     * ({@link #isAlreadyApplied})도 이 락 이후에 수행되어야 앞선 변동을 놓치지 않는다.
     */
    private PointBalanceEntity getOrCreateBalanceWithLock(Long memberId) {
        // 반드시 존재하는 회원 행을 잠근다 → 레코드 락만 잡힌다.
        MemberEntity member = memberRepository.findByIdWithPessimisticLock(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        try {
            return pointBalanceRepository.findByMember_MemberId(memberId)
                    .orElseGet(() -> pointBalanceRepository.save(PointBalanceEntity.init(member)));
        } catch (DataIntegrityViolationException e) {
            // 위 직렬화가 어긋나 중복 생성이 시도된 경우의 마지막 안전망.
            throw new BusinessException(ErrorCode.POINT_BUSY);
        }
    }

    /**
     * 이 주문에 대해 같은 타입의 변동이 이미 반영됐는지.
     *
     * <p>반드시 <b>잔액 행 락을 잡은 뒤</b>에 호출해야 한다. 락을 잡기 전에 읽으면 앞선
     * 트랜잭션이 커밋한 이력을 못 보고 중복 반영될 수 있다.
     *
     * <p>{@code orderId} 가 없는 변동(주문과 무관한 적립 등)은 멱등 판정 대상이 아니다.
     */
    private boolean isAlreadyApplied(Long memberId, Long orderId, PointHistoryType type) {
        return orderId != null
                && pointHistoryRepository.existsByMember_MemberIdAndOrderIdAndType(memberId, orderId, type);
    }

    /**
     * 변동 이력을 적재한다.
     *
     * <p>{@code (member_id, order_id, type)} 유니크 제약 위반은 위 멱등성 검사를 <b>동시에</b>
     * 통과한 요청이라는 뜻이다. 그대로 두면 커밋 시점에 잡히지 않은 예외로 500 이 나가므로,
     * 여기서 flush 해 즉시 감지하고 업무 예외로 변환한다. (쿠폰 발급과 같은 방식)
     */
    private void recordHistory(MemberEntity member, PointHistoryType type, long amount,
                               long balanceAfter, Long orderId) {
        try {
            pointHistoryRepository.saveAndFlush(
                    PointHistoryEntity.of(member, type, amount, balanceAfter, orderId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.POINT_ALREADY_PROCESSED);
        }
    }
}
