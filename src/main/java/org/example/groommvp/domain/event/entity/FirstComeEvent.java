package org.example.groommvp.domain.event.entity;

import org.example.groommvp.global.entity.BaseEntity;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "first_come_events")
public class FirstComeEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int limitCount;

    @Column(nullable = false)
    private int participatedCount;

    public FirstComeEvent(String name, int limitCount) {
        if (limitCount < 1) {
            throw new IllegalArgumentException("이벤트 수량은 1 이상이어야 합니다.");
        }
        this.name = name;
        this.limitCount = limitCount;
        this.participatedCount = 0;
    }

    public void participate() {
        if (participatedCount >= limitCount) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        participatedCount++;
    }

    public int getRemainingCount() {
        return limitCount - participatedCount;
    }
}
