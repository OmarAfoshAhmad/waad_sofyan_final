package com.waad.tba.modules.benefitpolicy.dto;

import lombok.Builder;

import java.util.Optional;
import java.util.List;

/** Canonical rule-resolution result shared by claims, eligibility and pre-authorization. */
@Builder
public record CoverageDecision(
        boolean covered,
        int coveragePercent,
        Long resolvedCategoryId,
        Long matchingCategoryId,
        CoverageDecisionSource source,
        String reasonCode,
        BenefitPolicyRuleResponseDto appliedRule,
        List<CoverageLimitSnapshot> limits) {

    public Optional<BenefitPolicyRuleResponseDto> appliedRuleOptional() {
        return Optional.ofNullable(appliedRule);
    }

    public List<CoverageLimitSnapshot> limitsOrEmpty() {
        return limits == null ? List.of() : List.copyOf(limits);
    }
}
