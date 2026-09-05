package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the immutable explanation of every limit used by final approval.
 *
 * P1.6.x: a pure Factory -- input is literally {@code Claim +
 * UnifiedLimitDecision + the resolved limit descriptors}, both already set
 * on each {@code ClaimLine} by {@code ClaimFinancialAdjudicationService}
 * from either Save-A's rider or its own fresh canonical resolve. It
 * re-resolves and re-queries NOTHING: no {@code EffectiveLimitResolver}, no
 * {@code ApplicableLimitResolver}, no {@code LimitBalanceReader}, no
 * {@code MultiLineMultiBucketEngine}, and no repository call of its own at
 * all (see {@link ClaimSaveHasNoLegacyLimitResolverTest}).
 *
 * Three separate facts feed one row, never four:
 * <pre>
 * UnifiedLimitDecision    -- numeric balance + what was allowed (consumptionTargets)
 * ResolvedLimitDescriptor -- identity: scope/beneficiary/group/period/source
 * WaadFinancialEngine     -- lineSettlementBase/lineInsideLimit/patientLimitExcess
 * </pre>
 * Every column below traces to exactly one of those three; none is a fresh
 * query, and none is invented. {@link UnifiedLimitDecision} deliberately
 * stays free of the descriptive half (benefitScopeType/periodType/etc. are
 * not part of "what is allowed") -- {@link ResolvedLimitDescriptor} is the
 * companion that carries it, captured once by {@code BucketLimitSnapshotAdapter}
 * at the exact same moment it already reads each bucket for the
 * BUCKET_POLICY_MISMATCH check, and paired 1:1 with its numeric sibling as a
 * {@link ResolvedLimitItem} so a row can never mix one bucket's numbers with
 * another bucket's description.
 *
 * One row per line per {@code consumptionTargets()} entry -- never per
 * {@code appliedBucketIds()} (a bucket the resolver merely evaluated but
 * did not consume, e.g. a parent ceiling that did not end up binding, is
 * never written as if it had a financial effect; the P1.6 design review
 * caught exactly this risk before it reached the table).
 *
 * claim_line_limit_snapshots has no TIMES/DAYS columns at all (a
 * pre-existing, already-documented gap -- see the P1.1 baseline). This
 * factory therefore only ever writes the AMOUNT axis; a consumption target
 * on the TIMES or DAYS axis is not represented here rather than forced
 * into an amount column it does not describe. Closing that gap is P1.11
 * (Snapshot = Ledger), not this phase.
 */
@Component
@RequiredArgsConstructor
public class ClaimLimitSnapshotFactory {
    private final EntityManager entityManager;
    private final com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;

    public List<ClaimLineLimitSnapshot> build(
            Claim claim, ClaimFinancialAdjudicationService.AdjudicationResult adjudication) {
        List<ClaimLine> lines = claim.getLines();
        List<WaadFinancialEngine.Result> lineResults = adjudication.lineResults();
        if (lines.size() != lineResults.size()) {
            throw new IllegalStateException("LIMIT_SNAPSHOT_LINE_COUNT_MISMATCH");
        }
        Long assignmentId = memberPolicyResolver
                .resolveAssignmentFor(claim.getMember(), claim.getServiceDate())
                .map(a -> a.getId()).orElse(null);

        List<ClaimLineLimitSnapshot> snapshots = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            ClaimLine line = lines.get(lineIndex);
            WaadFinancialEngine.Result financial = lineResults.get(lineIndex);
            UnifiedLimitDecision decision = line.getUnifiedLimitDecision();
            if (decision == null) {
                // BLOCKED already halted adjudication before this point;
                // reaching here with no decision at all is a caller defect,
                // not a business outcome -- fail closed rather than write a
                // row with fabricated numbers.
                throw new IllegalStateException(
                        "LIMIT_SNAPSHOT_MISSING_DECISION: claimLineId=" + line.getId());
            }
            // SMD5: a lookup keyed by limitKey -- never by array position --
            // so a target's numbers can only ever be paired with ITS OWN
            // descriptor, never a different bucket's.
            Map<String, ResolvedLimitDescriptor> descriptorByLimitKey = (line.getResolvedLimitItems() == null
                    ? List.<ResolvedLimitItem>of() : line.getResolvedLimitItems()).stream()
                    .collect(Collectors.toMap(item -> item.descriptor().limitKey(), ResolvedLimitItem::descriptor,
                            (first, ignored) -> first));

            int consumptionOrder = 1;
            for (BucketLimitSnapshot target : decision.consumptionTargets()) {
                if (target.limitType() != com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType.AMOUNT) {
                    continue; // no TIMES/DAYS columns exist on this table -- see class javadoc
                }
                String limitKey = target.bucketId() == null
                        ? ResolvedLimitDescriptor.policyGeneralKey(target.owningPolicyId())
                        : ResolvedLimitDescriptor.bucketKey(target.bucketId());
                ResolvedLimitDescriptor descriptor = descriptorByLimitKey.get(limitKey);
                if (descriptor == null) {
                    // The decision consumed a limit whose descriptor is missing --
                    // a caller defect (descriptors must be captured in the SAME
                    // resolution pass as the decision), not something to
                    // silently fall back from.
                    throw new IllegalStateException(
                            "LIMIT_SNAPSHOT_MISSING_DESCRIPTOR: claimLineId=" + line.getId()
                                    + " limitKey=" + limitKey);
                }

                BigDecimal availableBefore = target.remaining();
                BigDecimal consumption = financial.limitConsumption() == null ? BigDecimal.ZERO : financial.limitConsumption();
                BigDecimal availableAfter = availableBefore.subtract(consumption).max(BigDecimal.ZERO);

                snapshots.add(ClaimLineLimitSnapshot.builder()
                        .claim(claim).claimLine(line)
                        .calculationVersion(line.getCalculationVersion())
                        .benefitScopeType(descriptor.benefitScopeType())
                        .beneficiaryScopeType(descriptor.beneficiaryScopeType())
                        .limitSemanticKey(limitKey)
                        .bucket(reference(BenefitLimitBucket.class, descriptor.bucketId()))
                        .policy(reference(BenefitPolicy.class, target.owningPolicyId()))
                        .benefitRule(reference(BenefitPolicyRule.class, descriptor.benefitRuleId()))
                        .benefitGroup(reference(BenefitGroup.class, descriptor.benefitGroupId()))
                        .sourceType(descriptor.sourceType())
                        .sourceId(target.owningPolicyId())
                        .sourceVersion(null)
                        .structureRevision(null)
                        .memberPolicyAssignmentId(assignmentId)
                        .periodType(descriptor.periodType())
                        .periodStart(target.periodStart())
                        .periodEnd(target.periodEnd())
                        .effectiveLimit(target.configured())
                        .consumedBefore(target.committed())
                        .reservedBefore(target.activeReserved() == null ? BigDecimal.ZERO : target.activeReserved())
                        .availableBefore(availableBefore)
                        .lineSettlementBase(financial.settlementBase())
                        .lineInsideLimit(financial.insideLimit())
                        .limitConsumption(consumption)
                        .patientLimitExcess(financial.patientLimitExcess())
                        .availableAfter(availableAfter)
                        .binding(target.bucketId() != null && target.bucketId().equals(decision.bindingBucketId()))
                        .consumptionOrder(consumptionOrder++)
                        .build());
            }
        }
        return List.copyOf(snapshots);
    }

    private <T> T reference(Class<T> type, Long id) {
        return id == null ? null : entityManager.getReference(type, id);
    }
}
