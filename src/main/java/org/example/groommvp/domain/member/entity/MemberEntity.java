package org.example.groommvp.domain.member.entity;

import org.example.groommvp.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_MEMBERS_PROVIDER_ID",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MemberStatus status;

    @Builder
    private MemberEntity(
            AuthProvider provider,
            String providerId,
            String email,
            String nickname,
            MemberRole role,
            MemberStatus status
    ) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.status = status;
    }

    public static MemberEntity createKakaoMember(String providerId, String email, String nickname) {
        return MemberEntity.builder()
                .provider(AuthProvider.KAKAO)
                .providerId(providerId)
                .email(email)
                .nickname(nickname)
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    public void updateProfile(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }
}
