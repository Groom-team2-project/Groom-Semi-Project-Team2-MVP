package org.example.groommvp.domain.coupon.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.example.groommvp.domain.coupon.entity.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<CouponEntity, Long> {

    /**
     * 발급용 조회. 쿠폰 행에 비관적 쓰기 락을 걸어 동시 발급을 직렬화한다.
     *
     * <p>선착순 수량({@code issuedQuantity}) 증가는 read-modify-write 라서,
     * 락 없이는 동시 요청이 같은 값을 읽고 각자 증가시켜 초과 발급이 발생한다.
     * 재고 차감({@code StockRepository}) 과 같은 패턴이다.
     *
     * <p><b>락 타임아웃:</b> 인기 쿠폰처럼 같은 행에 요청이 몰릴 때 스레드가 락을 무한정
     * 기다리지 않도록 {@code jakarta.persistence.lock.timeout}(ms) 을 짧게 준다. 타임아웃 시
     * 발생하는 락 획득 실패는 서비스에서 {@link org.example.groommvp.global.error.ErrorCode#COUPON_ISSUE_BUSY}
     * 로 변환한다. (지원 여부는 DB/dialect 에 따르며, 미지원이면 무시된다.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from CouponEntity c where c.couponId = :couponId")
    Optional<CouponEntity> findByIdWithPessimisticLock(@Param("couponId") Long couponId);
}
