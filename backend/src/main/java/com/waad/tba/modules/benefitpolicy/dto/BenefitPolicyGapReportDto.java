package com.waad.tba.modules.benefitpolicy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structural gaps found in one policy's coverage configuration, ahead of
 * activation. None of these gaps mean the policy is invalid JPA-wise --
 * every row involved satisfies its own table's constraints -- they mean the
 * configuration cannot do what it looks like it does: a rule with no
 * bucket collects no limit, a bucket no rule reaches is dead configuration
 * that will never be read.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitPolicyGapReportDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RuleWithoutBucket {
        private Long ruleId;
        private String medicalCategoryCode;
        private String medicalCategoryName;
        private String claimContextCode;
        private String encounterType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BucketWithoutRule {
        private Long bucketId;
        private String bucketCode;
        private String bucketNameAr;
    }

    /**
     * Mirrors exactly the two ways CoverageDecisionService#resolve rejects
     * an explicit claim context (a row that never existed vs. one that
     * exists but is switched off) -- distinguished because the fix differs:
     * MISSING needs the context created, DISABLED needs it re-enabled or
     * the rule removed.
     */
    public enum ContextGapReason { MISSING, DISABLED }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RuleWithUnknownContext {
        private Long ruleId;
        private String medicalCategoryCode;
        private String claimContextCode;
        private ContextGapReason reason;
    }

    @Builder.Default
    private List<RuleWithoutBucket> rulesWithoutBucket = List.of();
    @Builder.Default
    private List<BucketWithoutRule> bucketsWithoutRule = List.of();
    @Builder.Default
    private List<RuleWithUnknownContext> rulesWithUnknownContext = List.of();

    /**
     * Only a rule naming a claim_context_code with no matching active
     * {@code claim_contexts} row is treated as critical: that rule can
     * never apply to any claim, in any context, which is unambiguously a
     * configuration bug rather than a deliberate choice.
     *
     * rulesWithoutBucket and bucketsWithoutRule are advisory, not critical:
     * a coverage rule legitimately needs no bucket at all when only the
     * policy's general annual ceiling should apply to it (bucket is an
     * independent, optional limit layer -- see BenefitPolicyRule's own
     * javadoc), and a bucket with no rule yet may simply be prepared ahead
     * of the rule that will reference it.
     */
    public boolean hasCriticalGaps() {
        return !rulesWithUnknownContext.isEmpty();
    }

    public boolean hasAnyGaps() {
        return hasCriticalGaps() || !rulesWithoutBucket.isEmpty() || !bucketsWithoutRule.isEmpty();
    }
}
