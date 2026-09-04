package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyGapReportDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyGapReportDto.BucketWithoutRule;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyGapReportDto.ContextGapReason;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyGapReportDto.RuleWithUnknownContext;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyGapReportDto.RuleWithoutBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket.LimitRole;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Answers "what in this policy's rule/bucket configuration is structurally
 * broken or dead?" -- read-only, diagnostic, never writes.
 *
 * Deliberately mirrors the exact two checks the LIVE coverage-calculation
 * path performs, not a parallel reimplementation of what "should" be
 * checked:
 *
 * 1. Claim-context validity: {@link com.waad.tba.modules.benefitpolicy.service.CoverageDecisionService#resolve}
 *    looks up the rule's exact claim_context_code in claim_contexts and
 *    rejects it (CONTEXT_MISMATCH) when the row is absent OR present but
 *    inactive -- this audit reproduces that same null-vs-inactive
 *    distinction (see {@link ContextGapReason}) rather than a coarser
 *    "is it in the active set" check.
 *
 * 2. Bucket reachability: uses {@link BucketChainWalker}, the same shared
 *    walk {@link BenefitBucketLimitService#findApplicable} (the live path)
 *    now calls -- this audit and the live engine used to each carry their
 *    own copy of this traversal, and the copies disagreed (the live one
 *    had no cycle guard at all -- a corrupt policy would hang a claim
 *    request forever instead of failing). Consolidating them means this
 *    audit's "reachable" answer is now, structurally, guaranteed to match
 *    what a real claim sees -- not a parallel reimplementation that could
 *    silently drift from it again.
 *
 * {@link ApplicableLimitResolver}, a second, apparently-not-yet-wired
 * engine, still performs an additional BUCKET_POLICY_MISMATCH check this
 * audit and the live path do not. Adding that check to the live path
 * unconditionally would change behavior for any claim served today by an
 * inconsistent parent chain, if one exists -- unmeasured, and deliberately
 * out of scope here; ApplicableLimitResolver itself was updated to use
 * BucketChainWalker too, so at least the mechanical walk no longer drifts
 * even though this one policy-level rule is not yet shared.
 */
@Service
@RequiredArgsConstructor
public class BenefitPolicyGapAuditService {

    private final BenefitPolicyRuleRepository ruleRepository;
    private final BenefitRuleBucketRepository ruleBucketRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final ClaimContextDefinitionRepository claimContextRepository;

    @Transactional(readOnly = true)
    public BenefitPolicyGapReportDto audit(Long policyId) {
        // Active, non-deleted only -- a disabled or soft-deleted rule can
        // never be selected by CoverageDecisionService either, so it is not
        // a gap for anyone to fix.
        List<BenefitPolicyRule> activeRules = ruleRepository
                .findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(policyId);
        List<BenefitRuleBucket> links = ruleBucketRepository
                .findByRuleBenefitPolicyIdOrderByConsumptionOrder(policyId);
        List<BenefitLimitBucket> buckets = bucketRepository.findByPolicyIdOrderByCode(policyId);

        Set<Long> rulesWithLinks = new HashSet<>();
        // BucketChainWalker is the same shared traversal
        // BenefitBucketLimitService#findApplicable (the live path) now
        // uses -- one implementation, not this audit's own copy of it.
        Set<Long> reachableBucketIds = new HashSet<>();
        for (BenefitRuleBucket link : links) {
            rulesWithLinks.add(link.getRule().getId());
            for (BenefitLimitBucket b : BucketChainWalker.chainFrom(link.getBucket())) {
                reachableBucketIds.add(b.getId());
            }
        }

        List<RuleWithoutBucket> rulesWithoutBucket = activeRules.stream()
                .filter(r -> !rulesWithLinks.contains(r.getId()))
                .map(r -> RuleWithoutBucket.builder()
                        .ruleId(r.getId())
                        .medicalCategoryCode(r.getMedicalCategory() != null ? r.getMedicalCategory().getCode() : null)
                        .medicalCategoryName(r.getMedicalCategory() != null
                                ? (r.getMedicalCategory().getNameAr() != null ? r.getMedicalCategory().getNameAr()
                                        : r.getMedicalCategory().getName())
                                : null)
                        .claimContextCode(r.getClaimContextCode())
                        .encounterType(r.getEncounterType() != null ? r.getEncounterType().name() : null)
                        .build())
                .toList();

        List<BucketWithoutRule> bucketsWithoutRule = buckets.stream()
                .filter(b -> b.isActive() && b.getLimitRole() != LimitRole.POLICY_GENERAL_MIRROR)
                .filter(b -> !reachableBucketIds.contains(b.getId()))
                .map(b -> BucketWithoutRule.builder()
                        .bucketId(b.getId()).bucketCode(b.getCode()).bucketNameAr(b.getNameAr()).build())
                .toList();

        List<RuleWithUnknownContext> rulesWithUnknownContext = activeRules.stream()
                .filter(r -> r.getClaimContextCode() != null && !r.getClaimContextCode().isBlank())
                .<RuleWithUnknownContext>mapMulti((r, sink) -> {
                    // Exactly CoverageDecisionService.resolve's own
                    // normalization before its claim_contexts lookup.
                    String normalized = r.getClaimContextCode().trim().toUpperCase(Locale.ROOT);
                    var definition = claimContextRepository.findById(normalized).orElse(null);
                    ContextGapReason reason = definition == null ? ContextGapReason.MISSING
                            : !definition.isActive() ? ContextGapReason.DISABLED
                            : null;
                    // A defined-and-active context whose baseEncounterType
                    // conflicts with the rule's own encounterType is the
                    // third way CoverageDecisionService rejects a context
                    // (CONTEXT_MISMATCH) -- folded into DISABLED here since
                    // the fix is the same category of action (the rule's
                    // encounterType or the context's baseEncounterType needs
                    // reconciling), not a fourth report bucket.
                    if (reason == null && definition.getBaseEncounterType() != EncounterType.ANY
                            && r.getEncounterType() != null
                            && definition.getBaseEncounterType() != r.getEncounterType()) {
                        reason = ContextGapReason.DISABLED;
                    }
                    if (reason != null) {
                        sink.accept(RuleWithUnknownContext.builder()
                                .ruleId(r.getId())
                                .medicalCategoryCode(r.getMedicalCategory() != null
                                        ? r.getMedicalCategory().getCode() : null)
                                .claimContextCode(r.getClaimContextCode())
                                .reason(reason)
                                .build());
                    }
                })
                .toList();

        return BenefitPolicyGapReportDto.builder()
                .rulesWithoutBucket(rulesWithoutBucket)
                .bucketsWithoutRule(bucketsWithoutRule)
                .rulesWithUnknownContext(rulesWithUnknownContext)
                .build();
    }
}
