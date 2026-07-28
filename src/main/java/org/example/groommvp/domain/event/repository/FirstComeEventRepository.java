package org.example.groommvp.domain.event.repository;

import java.util.Optional;

import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** 이벤트 조회/저장 담당 */
public interface FirstComeEventRepository extends JpaRepository<FirstComeEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from FirstComeEvent e where e.id = :eventId")
    Optional<FirstComeEvent> findByIdWithPessimisticLock(
            @Param("eventId") Long eventId
    );
}
