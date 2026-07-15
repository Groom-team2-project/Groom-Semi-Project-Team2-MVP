package org.example.groommvp.domain.auth.service;

import org.example.groommvp.domain.auth.dto.KakaoUserInfo;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthMemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public MemberLookupResult findOrCreateMember(KakaoUserInfo userInfo) {
        MemberLookupResult lookupResult = memberRepository
                .findByProviderAndProviderId(AuthProvider.KAKAO, userInfo.providerId())
                .map(member -> {
                    member.updateProfile(userInfo.email(), userInfo.nickname());
                    return new MemberLookupResult(member, false);
                })
                .orElseGet(() -> createMember(userInfo));

        validateLoginAllowed(lookupResult.member());
        return lookupResult;
    }

    private MemberLookupResult createMember(KakaoUserInfo userInfo) {
        MemberEntity member = MemberEntity.createKakaoMember(
                userInfo.providerId(),
                userInfo.email(),
                userInfo.nickname()
        );

        try {
            return new MemberLookupResult(memberRepository.saveAndFlush(member), true);
        } catch (DataIntegrityViolationException e) {
            MemberEntity existingMember = memberRepository
                    .findByProviderAndProviderId(AuthProvider.KAKAO, userInfo.providerId())
                    .orElseThrow(() -> e);
            existingMember.updateProfile(userInfo.email(), userInfo.nickname());
            return new MemberLookupResult(existingMember, false);
        }
    }

    private void validateLoginAllowed(MemberEntity member) {
        if (!member.isLoginAllowed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그인할 수 없는 회원 상태입니다.");
        }
    }

    public record MemberLookupResult(
            MemberEntity member,
            boolean newMember
    ) {
    }
}
