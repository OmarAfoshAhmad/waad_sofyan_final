package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Builds the immutable explanation of every limit used by final approval. */
@Component
@RequiredArgsConstructor
public class ClaimLimitSnapshotFactory {
    private final EntityManager entityManager;

    public List<ClaimLineLimitSnapshot> build(
            Claim claim, ClaimFinancialAdjudicationService.AdjudicationResult adjudication) {
        var lineResults = adjudication.claimResult().lines();
        if (claim.getLines().size() != lineResults.size()) {
            throw new IllegalStateException("LIMIT_SNAPSHOT_LINE_COUNT_MISMATCH");
        }
        List<ClaimLineLimitSnapshot> snapshots = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < claim.getLines().size(); lineIndex++) {
            ClaimLine line = claim.getLines().get(lineIndex);
            var result = lineResults.get(lineIndex);
            for (var allocation : result.limitAllocations()) {
                var effective = allocation.balance().limit();
                var definition = effective.definition();
                Long sourceId = switch (effective.sourceType()) {
                    case POLICY_DEFAULT -> definition.policyId();
                    case EMPLOYER_OVERRIDE -> effective.sourceEmployerId();
                    case MEMBER_OVERRIDE -> effective.sourceMemberId();
                };
                BenefitPolicy policy = reference(BenefitPolicy.class, definition.policyId());
                snapshots.add(ClaimLineLimitSnapshot.builder()
                        .claim(claim).claimLine(line)
                        .calculationVersion(line.getCalculationVersion())
                        .benefitScopeType(definition.benefitScopeType())
                        .beneficiaryScopeType(definition.beneficiaryScopeType())
                        .limitSemanticKey(definition.semanticKey())
                        .bucket(reference(BenefitLimitBucket.class, definition.bucketId()))
                        .policy(policy)
                        .benefitRule(reference(BenefitPolicyRule.class, definition.benefitRuleId()))
                        .benefitGroup(reference(BenefitGroup.class, definition.benefitGroupId()))
                        .sourceType(effective.sourceType()).sourceId(sourceId)
                        .sourceVersion(policy.getVersion()).structureRevision(null)
                        .periodType(definition.periodType())
                        .periodStart(definition.periodStart()).periodEnd(definition.periodEnd())
                        .effectiveLimit(allocation.effectiveLimit())
                        .consumedBefore(allocation.committedBeforeClaim())
                        .reservedBefore(allocation.reservedBeforeClaim())
                        .availableBefore(allocation.availableBeforeLine())
                        .lineSettlementBase(result.financial().settlementBase())
                        .lineInsideLimit(result.financial().insideLimit())
                        .limitConsumption(allocation.consumption())
                        .patientLimitExcess(result.financial().patientLimitExcess())
                        .availableAfter(allocation.availableAfterLine())
                        .binding(allocation.binding())
                        .consumptionOrder(definition.consumptionOrder())
                        .build());
            }
        }
        return List.copyOf(snapshots);
    }

    private <T> T reference(Class<T> type, Long id) {
        return id == null ? null : entityManager.getReference(type, id);
    }
}
