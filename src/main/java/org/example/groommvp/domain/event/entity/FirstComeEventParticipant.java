package org.example.groommvp.domain.event.entity;

import org.example.groommvp.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "first_come_event_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_participant_event_member",
                columnNames = {"event_id", "member_id"}
        )
)
public class FirstComeEventParticipant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private FirstComeEvent event; // 어떤 이벤트에 참여했는지

    @Column(name = "member_id", nullable = false)
    private Long memberId; // 어떤 회원이 참여했는지

    public FirstComeEventParticipant(FirstComeEvent event, Long memberId) {
        this.event = event;
        this.memberId = memberId;
    }

}
