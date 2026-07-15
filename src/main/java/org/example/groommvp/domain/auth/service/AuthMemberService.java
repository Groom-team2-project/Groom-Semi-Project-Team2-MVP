package org.example.groommvp.domain.auth.service;

import java.util.Objects;

import org.example.groommvp.domain.auth.dto.KakaoUserInfo;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthMemberService {

    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    public MemberLookupResult findOrCreateMember(KakaoUserInfo userInfo) {
        try {
            return validateAndReturn(executeInTransaction(() -> findOrCreateMemberInTransaction(userInfo)));
        } catch (DataIntegrityViolationException e) {
            if (!isProviderIdUniqueViolation(e)) {
                throw e;
            }
            return validateAndReturn(executeInTransaction(() -> findExistingMemberInTransaction(userInfo)));
        }
    }

    private MemberLookupResult findOrCreateMemberInTransaction(KakaoUserInfo userInfo) {
        return memberRepository
                .findByProviderAndProviderId(AuthProvider.KAKAO, userInfo.providerId())
                .map(member -> {
                    member.updateProfile(userInfo.email(), userInfo.nickname());
                    return new MemberLookupResult(member, false);
                })
                .orElseGet(() -> createMember(userInfo));
    }

    private MemberLookupResult createMember(KakaoUserInfo userInfo) {
        MemberEntity member = MemberEntity.createKakaoMember(
                userInfo.providerId(),
                userInfo.email(),
                userInfo.nickname()
        );

        return new MemberLookupResult(memberRepository.saveAndFlush(member), true);
    }

    private MemberLookupResult findExistingMemberInTransaction(KakaoUserInfo userInfo) {
        MemberEntity existingMember = memberRepository
                .findByProviderAndProviderId(AuthProvider.KAKAO, userInfo.providerId())
                .orElseThrow(() -> new DataIntegrityViolationException("Concurrent member creation recovery failed."));
        existingMember.updateProfile(userInfo.email(), userInfo.nickname());
        return new MemberLookupResult(existingMember, false);
    }

    private MemberLookupResult executeInTransaction(MemberLookupCallback callback) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> callback.execute()));
    }

    private MemberLookupResult validateAndReturn(MemberLookupResult lookupResult) {
        validateLoginAllowed(lookupResult.member());
        return lookupResult;
    }

    private void validateLoginAllowed(MemberEntity member) {
        if (!member.isLoginAllowed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Login is not allowed for this member status.");
        }
    }

    private boolean isProviderIdUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (
                    message.contains("UK_MEMBERS_PROVIDER_ID")
                            || message.contains("provider_id")
                            || message.contains("members.provider")
            )) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    private interface MemberLookupCallback {

        MemberLookupResult execute();
    }

    public record MemberLookupResult(
            MemberEntity member,
            boolean newMember
    ) {
    }
}
