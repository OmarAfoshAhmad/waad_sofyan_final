package com.waad.tba.modules.member.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.error.ErrorCode;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** Resolves employer and policy from the same explicit service date. */
@Service
@RequiredArgsConstructor
public class MemberContextResolver {

    private final MemberEmployerResolver employerResolver;
    private final MemberPolicyResolver policyResolver;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberDatedContext resolveForOrFail(Long memberId, LocalDate serviceDate) {
        if (memberId == null) {
            throw new BusinessRuleException("معرّف المستفيد مطلوب لحل السياق الزمني");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessRuleException("المستفيد غير موجود: " + memberId));
        return resolveForOrFail(member, serviceDate);
    }

    @Transactional(readOnly = true)
    public MemberDatedContext resolveForOrFail(Member member, LocalDate serviceDate) {
        if (member == null || member.getId() == null) {
            throw new BusinessRuleException("المستفيد مطلوب لحل السياق الزمني");
        }
        if (serviceDate == null) {
            throw new BusinessRuleException(
                    "تاريخ الخدمة إلزامي لحل سياق المستفيد، ولا يجوز استبداله بتاريخ اليوم");
        }

        var employerAssignment = employerResolver.resolveAssignmentFor(member, serviceDate)
                .orElseThrow(() -> notCovered(serviceDate));
        var employer = employerResolver.resolveFor(member, serviceDate)
                .orElseThrow(() -> notCovered(serviceDate));
        var policyAssignment = policyResolver.resolveAssignmentFor(member, serviceDate)
                .orElseThrow(() -> notCovered(serviceDate));
        var policy = policyResolver.resolveFor(member, serviceDate)
                .orElseThrow(() -> notCovered(serviceDate));

        Long policyEmployerId = policy.getEmployer() == null ? null : policy.getEmployer().getId();
        if (policyEmployerId == null || !employer.getId().equals(policyEmployerId)) {
            throw notCovered(serviceDate);
        }

        return new MemberDatedContext(member.getId(), serviceDate,
                employerAssignment, employer, policyAssignment, policy);
    }

    private static BusinessRuleException notCovered(LocalDate serviceDate) {
        String displayedDate = serviceDate.format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        return new BusinessRuleException(
                ErrorCode.MEMBER_NOT_COVERED_AT_SERVICE_DATE,
                "المستفيد غير مغطى تأمينياً بتاريخ " + displayedDate + ".");
    }
}
