package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Supplies one explicit source for an applicable limit; it never reads usage. */
public interface LimitSourceProvider {

    record ResolvedSource(SourceType sourceType, BigDecimal effectiveLimit,
                          Long sourceEmployerId, Long sourceMemberId) {}

    int priority();

    Optional<ResolvedSource> resolve(
            ApplicableLimitResolver.ApplicableLimitDefinition definition,
            Long memberId,
            LocalDate serviceDate);
}
