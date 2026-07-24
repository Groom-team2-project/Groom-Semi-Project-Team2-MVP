package org.example.groommvp.domain.event.repository;

import org.example.groommvp.domain.event.entity.FirstComeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** 이벤트 조회/저장 담당 */
public interface FirstComeEventRepository extends JpaRepository<FirstComeEvent, Long> {
}
