package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Authoritative dated context shown before a claim can be calculated. */
public record ClaimEntryContextDto(
        Long memberId,
        LocalDate serviceDate,
        Long employerAssignmentId,
        Long employerId,
        String employerName,
        Long policyAssignmentId,
        Long policyId,
        String policyCode,
        String policyName,
        String policyStatus,
        LocalDate policyStartDate,
        LocalDate policyEndDate,
        Long contractId,
        Long contractTermsId,
        String contractCode,
        String contractNumber,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        String ceilingMode,
        BigDecimal annualLimit,
        BigDecimal committedAmount,
        BigDecimal reservedAmount,
        BigDecimal actualRemaining,
        BigDecimal reservableAvailable,
        LocalDateTime balanceReadAt,
        List<EligiblePreAuthorizationDto> eligiblePreAuthorizations) {
}
