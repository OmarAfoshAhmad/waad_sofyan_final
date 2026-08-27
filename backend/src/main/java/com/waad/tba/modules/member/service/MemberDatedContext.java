package com.waad.tba.modules.member.service;

import java.time.LocalDate;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.MemberEmployerAssignment;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;

/** Reproducible member context for one explicit business date. */
public record MemberDatedContext(
        Long memberId,
        LocalDate serviceDate,
        MemberEmployerAssignment employerAssignment,
        Employer employer,
        MemberPolicyAssignment policyAssignment,
        BenefitPolicy policy) {
}
