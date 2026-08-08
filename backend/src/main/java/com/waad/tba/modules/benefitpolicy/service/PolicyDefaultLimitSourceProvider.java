package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** Current constitutional source: every member receives the policy limit independently. */
@Component
public class PolicyDefaultLimitSourceProvider implements LimitSourceProvider {
    @Override public int priority() { return 0; }

    @Override
    public Optional<ResolvedSource> resolve(
            ApplicableLimitResolver.ApplicableLimitDefinition definition,
            Long memberId,
            LocalDate serviceDate) {
        if (definition.beneficiaryScopeType() != BeneficiaryScopeType.MEMBER) {
            throw new IllegalStateException("UNSUPPORTED_BENEFICIARY_SCOPE: "
                    + definition.beneficiaryScopeType() + " for " + definition.semanticKey());
        }
        return Optional.of(new ResolvedSource(
                SourceType.POLICY_DEFAULT, definition.defaultLimit(), null, null));
    }
}
