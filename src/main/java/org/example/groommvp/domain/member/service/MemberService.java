package org.example.groommvp.domain.member.service;

import org.example.groommvp.domain.member.dto.MemberMeResponse;
import org.example.groommvp.domain.member.dto.MemberUpdateRequest;
import org.example.groommvp.domain.member.entity.MemberEntity;
import org.example.groommvp.domain.member.repository.MemberRepository;
import org.example.groommvp.global.error.BusinessException;
import org.example.groommvp.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberMeResponse getMe(Long memberId) {
        return MemberMeResponse.from(getMember(memberId));
    }

    /** 내 프로필 수정. (이메일/닉네임 부분 수정 — null/공백은 무시된다) */
    @Transactional
    public MemberMeResponse updateMe(Long memberId, MemberUpdateRequest request) {
        MemberEntity member = getMember(memberId);
        member.updateProfile(request.email(), request.nickname());
        return MemberMeResponse.from(member);
    }

    private MemberEntity getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
