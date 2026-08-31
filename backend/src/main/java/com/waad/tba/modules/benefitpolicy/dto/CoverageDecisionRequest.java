package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Canonical immutable input for resolving a benefit coverage rule. */
@Builder
public record CoverageDecisionRequest(
        Long policyId,
        Long serviceId,
        Long serviceCategoryId,
        Long overrideCategoryId,
        Long memberId,
        LocalDate serviceDate,
        Long excludeClaimId,
        EncounterType encounterType,
        String claimContextCode,
        Double classificationConfidence,
        BigDecimal requestedAmount) {
}
