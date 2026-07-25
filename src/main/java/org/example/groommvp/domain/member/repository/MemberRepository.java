package org.example.groommvp.domain.member.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findByProviderAndProviderId(AuthProvider provider, String providerId);

    long countByProviderAndProviderId(AuthProvider provider, String providerId);

    /**
     * 회원 행에 비관적 쓰기 락을 걸어 조회한다.
     *
     * <p>회원에 종속된 1:1 레코드(예: 포인트 잔액)를 <b>최초 생성</b>할 때, 동시에 들어온
     * 요청들이 같은 레코드를 중복 삽입하려다 유니크 제약으로 실패하는 것을 막기 위해
     * 회원 행을 잠가 생성 구간을 직렬화하는 용도.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemberEntity m where m.memberId = :memberId")
    Optional<MemberEntity> findByIdWithPessimisticLock(@Param("memberId") Long memberId);
}
