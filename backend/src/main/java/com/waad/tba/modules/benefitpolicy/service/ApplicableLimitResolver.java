package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket.LimitRole;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WAAD-FIN-1.0: answers exactly one question -- "what limits apply to this
 * claim line, on this service date?" -- and nothing else. Purely structural:
 * no override selection (see {@link EffectiveLimitResolver}), no
 * consumption/reservation reading (see {@link LimitBalanceReader}), no
 * financial calculation, no persistence. Those stay
 * separate on purpose so each can be tested, reasoned about, and eventually
 * replaced independently -- see backend/docs/design/FINANCIAL_CONSTITUTION.md.
 *
 * The general annual ceiling is always represented exactly once, as the
 * synthetic {@code POLICY_GENERAL:{policyId}} entry built from
 * {@link BenefitPolicy#getAnnualLimit()}; any bucket tagged
 * {@link LimitRole#POLICY_GENERAL_MIRROR} (V146) is excluded from the result
 * after its amount/period is verified to still match the policy it mirrors.
 */
@Service
@RequiredArgsConstructor
public class ApplicableLimitResolver {

    private final BenefitPolicyRuleRepository ruleRepository;
    private final BenefitRuleBucketRepository ruleBucketRepository;

    /**
     * One resolved limit definition -- structural only, no balance/consumption
     * data. {@code hierarchyDepth} is 0 for a bucket directly linked to the
     * rule via {@link BenefitRuleBucket}, and increases by 1 per
     * {@code parentBucket} hop. Scope is an explicit bucket property and is
     * never inferred from hierarchy depth.
     */
    public record ApplicableLimitDefinition(
            String semanticKey,
            BenefitScopeType benefitScopeType,
            BeneficiaryScopeType beneficiaryScopeType,
            Long bucketId,
            Long policyId,
            Long benefitRuleId,
            Long benefitGroupId,
            BigDecimal defaultLimit,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            int consumptionOrder,
            int hierarchyDepth) {
    }

    @Transactional(readOnly = true)
    public List<ApplicableLimitDefinition> resolve(Long policyId, Long benefitRuleId,
                                                     LocalDate serviceDate, EncounterType encounterType) {
        if (policyId == null || benefitRuleId == null || serviceDate == null || encounterType == null) {
            throw new IllegalArgumentException(
                    "ApplicableLimitResolver.resolve requires policyId, benefitRuleId, serviceDate and encounterType");
        }

        BenefitPolicyRule rule = ruleRepository.findById(benefitRuleId)
                .orElseThrow(() -> new IllegalStateException("BENEFIT_RULE_NOT_FOUND: id=" + benefitRuleId));
        BenefitPolicy policy = rule.getBenefitPolicy();
        if (policy == null || !policyId.equals(policy.getId())) {
            throw new IllegalStateException("BENEFIT_RULE_POLICY_MISMATCH: rule id=" + benefitRuleId
                    + " belongs to policy id=" + (policy == null ? null : policy.getId())
                    + ", not the requested policy id=" + policyId);
        }

        Map<Long, ResolvedBucket> byId = new LinkedHashMap<>();
        for (BenefitRuleBucket link : ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(benefitRuleId)) {
            walkBucketChain(link.getBucket(), policyId, link.getConsumptionOrder(), 0, byId);
        }

        List<ApplicableLimitDefinition> result = new ArrayList<>();
        BenefitLimitBucket mirror = null;
        for (ResolvedBucket rb : byId.values()) {
            BenefitLimitBucket bucket = rb.bucket;
            if (bucket.getLimitRole() == LimitRole.POLICY_GENERAL_MIRROR) {
                if (mirror != null) {
                    throw new IllegalStateException("POLICY_GENERAL_MIRROR_MISMATCH: policy id=" + policyId
                            + " has more than one active POLICY_GENERAL_MIRROR bucket (ids " + mirror.getId()
                            + ", " + bucket.getId() + ")");
                }
                mirror = bucket;
                validateMirrorMatchesPolicy(bucket, policy);
                continue; // never emitted as its own applicable limit
            }
            if (!bucket.isActive()) continue;
            if (bucket.getContextType() != EncounterType.ANY && bucket.getContextType() != encounterType) continue;
            if (bucket.getBenefitScopeType() == null) {
                throw new IllegalStateException("BUCKET_SCOPE_NOT_CLASSIFIED: bucket id=" + bucket.getId());
            }
            if (bucket.getBeneficiaryScopeType() == null) {
                throw new IllegalStateException("BUCKET_BENEFICIARY_SCOPE_NOT_CLASSIFIED: bucket id=" + bucket.getId());
            }

            if (bucket.getAmountLimit() == null) {
                if (bucket.getTimesLimit() != null || bucket.getDaysLimit() != null) {
                    // This resolver feeds the monetary WAAD-FIN engine. Count/day-only
                    // buckets remain enforced by CoverageEngineService and the bucket
                    // ledger; assigning them a synthetic monetary value would silently
                    // invent policy. Their unified quantity semantics are a separate
                    // constitutional decision (POLICY_DECISION_REQUIRED-03).
                    continue;
                }
                throw new IllegalStateException("BUCKET_LIMIT_VALUE_MISSING: bucket id=" + bucket.getId()
                        + " is active and applicable but has no valid amountLimit");
            }
            if (bucket.getAmountLimit().signum() < 0) {
                throw new IllegalStateException("BUCKET_LIMIT_VALUE_INVALID: bucket id=" + bucket.getId());
            }

            BucketPeriodCalculator.Period period = BucketPeriodCalculator.resolve(bucket, policy, serviceDate);
            result.add(new ApplicableLimitDefinition(
                    "BUCKET:" + bucket.getId(),
                    bucket.getBenefitScopeType(), bucket.getBeneficiaryScopeType(),
                    bucket.getId(), policyId, benefitRuleId,
                    bucket.getBenefitGroup() != null ? bucket.getBenefitGroup().getId() : null,
                    bucket.getAmountLimit(), bucket.getPeriodType().name(),
                    period.start(), period.end(),
                    rb.consumptionOrder, rb.hierarchyDepth));
        }

        if (policy.getAnnualLimit() != null) {
            BucketPeriodCalculator.Period annualPeriod = BucketPeriodCalculator.resolve(
                    com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType.ANNUAL, 1, policy, serviceDate);
            result.add(new ApplicableLimitDefinition(
                    "POLICY_GENERAL:" + policyId,
                    BenefitScopeType.POLICY_GENERAL, BeneficiaryScopeType.MEMBER,
                    null, policyId, benefitRuleId, null,
                    policy.getAnnualLimit(), "ANNUAL",
                    annualPeriod.start(), annualPeriod.end(),
                    Integer.MAX_VALUE, Integer.MAX_VALUE));
        }

        result.sort(Comparator.comparingInt(ApplicableLimitDefinition::consumptionOrder)
                .thenComparingInt(ApplicableLimitDefinition::hierarchyDepth)
                .thenComparing(ApplicableLimitDefinition::semanticKey));
        return result;
    }

    private record ResolvedBucket(BenefitLimitBucket bucket, int consumptionOrder, int hierarchyDepth) {
    }

    /**
     * The mechanical walk (self, then parentBucket, ...), including cycle
     * detection, is {@link BucketChainWalker#chainFrom}, shared with the
     * live coverage path and the gap audit -- chainFrom already guarantees
     * no bucket repeats within one call, so no second visited-set is kept
     * here. What stays unshared: the BUCKET_POLICY_MISMATCH check per
     * bucket -- see BenefitPolicyGapAuditService's javadoc for why that
     * check is not (yet) applied to the live path.
     */
    private void walkBucketChain(BenefitLimitBucket start, Long policyId, int consumptionOrder, int depth,
                                  Map<Long, ResolvedBucket> byId) {
        int currentDepth = depth;
        for (BenefitLimitBucket current : BucketChainWalker.chainFrom(start)) {
            if (current.getPolicy() == null || !policyId.equals(current.getPolicy().getId())) {
                throw new IllegalStateException("BUCKET_POLICY_MISMATCH: bucket id=" + current.getId()
                        + " belongs to policy id=" + (current.getPolicy() == null ? null : current.getPolicy().getId())
                        + ", not policy id=" + policyId);
            }
            byId.merge(current.getId(), new ResolvedBucket(current, consumptionOrder, currentDepth),
                    (existing, candidate) -> existing.hierarchyDepth <= candidate.hierarchyDepth ? existing : candidate);
            currentDepth++;
        }
    }

    private void validateMirrorMatchesPolicy(BenefitLimitBucket bucket, BenefitPolicy policy) {
        if (policy.getAnnualLimit() == null) {
            throw new IllegalStateException("POLICY_GENERAL_MIRROR_MISMATCH: bucket id=" + bucket.getId()
                    + " is tagged POLICY_GENERAL_MIRROR but policy id=" + policy.getId() + " has no annualLimit");
        }
        if (bucket.getPeriodType() != com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType.ANNUAL
                || bucket.getAmountLimit() == null
                || bucket.getAmountLimit().compareTo(policy.getAnnualLimit()) != 0) {
            throw new IllegalStateException("POLICY_GENERAL_MIRROR_MISMATCH: bucket id=" + bucket.getId()
                    + " (periodType=" + bucket.getPeriodType() + ", amountLimit=" + bucket.getAmountLimit()
                    + ") does not match policy id=" + policy.getId() + " annualLimit=" + policy.getAnnualLimit());
        }
    }
}
