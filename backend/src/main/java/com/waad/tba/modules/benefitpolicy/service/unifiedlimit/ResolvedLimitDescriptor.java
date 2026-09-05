package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;

import java.time.LocalDate;

/**
 * P1.6.x: the identity/description half of a resolved limit -- everything
 * {@code claim_line_limit_snapshots} needs to explain WHERE a limit came
 * from, deliberately kept OUT of {@link UnifiedLimitDecision} (which only
 * ever answers "what is allowed") and OUT of {@link BucketLimitSnapshot}
 * (which only ever carries the numeric balance -- configured/committed/
 * reserved/remaining -- {@link UnifiedLimitResolver} operates on). Three
 * separate facts, three separate types:
 * <pre>
 * UnifiedLimitDecision   -- what did the limit allow?
 * ResolvedLimitDescriptor -- what IS this limit, and where is it from?
 * WaadFinancialEngine     -- who pays how much?
 * </pre>
 *
 * Captured exactly once, at the same place {@link BucketLimitSnapshot} rows
 * are already built ({@code BucketLimitSnapshotAdapter}), from the SAME
 * {@code BenefitLimitBucket} entities that pass already fetches for the
 * BUCKET_POLICY_MISMATCH check -- never a second, dedicated query. Paired
 * 1:1 with its numeric sibling as a {@link ResolvedLimitItem}, so a
 * consumer can never accidentally read one bucket's numbers against
 * another bucket's description.
 *
 * {@code limitKey} is the stable identity a consumer keys on -- it is NOT
 * always {@code "BUCKET:" + bucketId}: the synthetic POLICY_GENERAL ceiling
 * has no bucket row at all, so its key is {@code "POLICY_GENERAL:" +
 * owningPolicyId} instead. Never assume bucketId alone is a safe key.
 */
public record ResolvedLimitDescriptor(
        String limitKey,
        Long bucketId,
        ClaimLineLimitSnapshot.SourceType sourceType,
        BenefitScopeType benefitScopeType,
        BeneficiaryScopeType beneficiaryScopeType,
        Long benefitRuleId,
        Long benefitGroupId,
        String periodType,
        LocalDate periodFrom,
        LocalDate periodTo) {

    public static String bucketKey(Long bucketId) {
        return "BUCKET:" + bucketId;
    }

    public static String policyGeneralKey(Long owningPolicyId) {
        return "POLICY_GENERAL:" + owningPolicyId;
    }
}
