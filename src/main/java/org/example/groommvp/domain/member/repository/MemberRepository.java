package org.example.groommvp.domain.member.repository;

import java.util.Optional;

import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findByProviderAndProviderId(AuthProvider provider, String providerId);

    long countByProviderAndProviderId(AuthProvider provider, String providerId);
}
