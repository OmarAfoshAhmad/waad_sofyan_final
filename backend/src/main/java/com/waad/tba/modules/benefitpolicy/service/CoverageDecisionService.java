package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.dto.*;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Single source of truth for selecting a benefit rule.
 * This service never calculates money and never consumes limits.
 */
@Service
@RequiredArgsConstructor
public class CoverageDecisionService {
    private static final String GENERAL_INPATIENT_CATEGORY_CODE = "CAT-COV-INPATIENT";
    private static final String GENERAL_OUTPATIENT_CATEGORY_CODE = "CAT-COV-OUTPATIENT";
    private static final java.util.Set<String> INPATIENT_BASED_CLAIM_CONTEXTS = java.util.Set.of(
            "INPATIENT", "MATERNITY", "PREGNANCY_COMPLICATIONS");

    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final BenefitBucketLimitService bucketLimitService;
    private final ClaimContextDefinitionRepository claimContextRepository;

    @Transactional(readOnly = true)
    public CoverageDecision resolve(CoverageDecisionRequest request) {
        Long categoryId = request.overrideCategoryId() != null
                ? request.overrideCategoryId() : request.serviceCategoryId();
        MedicalCategory category = categoryId == null ? null : categoryRepository.findById(categoryId).orElse(null);
        if (category == null || !category.isActive() || category.isDeleted()) {
            return rejected(categoryId, CoverageDecisionSource.INVALID_CATEGORY, "INVALID_CATEGORY");
        }
        if (request.requestedAmount() != null && request.requestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return rejected(categoryId, CoverageDecisionSource.PRICE_ZERO, "PRICE_ZERO");
        }
        if (request.classificationConfidence() != null && request.classificationConfidence() < 0.6d) {
            return rejected(categoryId, CoverageDecisionSource.LOW_CONFIDENCE, "LOW_CLASSIFICATION_CONFIDENCE");
        }

        EncounterType context = request.encounterType() != null ? request.encounterType() : EncounterType.OUTPATIENT;
        /*
         * Financial boundary:
         * - MedicalCategory is the service classification only.
         * - claimContextCode is the whole-claim financial context.
         *
         * Older data still carries medical_category_contexts, but that table must not
         * veto coverage decisions. A diagnostic category such as labs/imaging can be
         * valid in outpatient, inpatient, maternity and pregnancy-complication claims
         * depending on the policy rule. The exact rule lookup below is therefore the
         * single financial authority for category+claim-context eligibility.
         */

        BenefitPolicy policy = request.policyId() == null
                ? null : policyRepository.findById(request.policyId()).orElse(null);
        if (policy == null) {
            return rejected(categoryId, CoverageDecisionSource.NO_BENEFIT_RULE, "POLICY_NOT_FOUND");
        }
        if (policy.getExcludedCategoryCodes() != null
                && policy.getExcludedCategoryCodes().contains(category.getCode())) {
            return rejected(categoryId, CoverageDecisionSource.EXCLUDED_CATEGORY, "EXCLUDED_CATEGORY");
        }

        boolean explicitClaimContext = request.claimContextCode() != null && !request.claimContextCode().isBlank();
        String exactContext = explicitClaimContext
                ? request.claimContextCode().trim().toUpperCase(java.util.Locale.ROOT) : context.name();
        if (explicitClaimContext) {
            var definition = claimContextRepository.findById(exactContext).orElse(null);
            if (definition == null || !definition.isActive()) {
                return rejected(categoryId, CoverageDecisionSource.CONTEXT_MISMATCH, "CLAIM_CONTEXT_MISMATCH");
            }
            // Claim context is the financial boundary of the whole claim. If the UI or
            // an older draft sends a stale encounterType with a valid explicit context
            // (e.g. OUTPATIENT + PREGNANCY_COMPLICATIONS), the context must govern.
            // Otherwise a harmless UI round-trip turns an inpatient-based maternity
            // rule into "not covered" until the user toggles the context away and back.
            if (definition.getBaseEncounterType() != EncounterType.ANY) {
                context = definition.getBaseEncounterType();
            }
        }
        boolean inpatientBasedContext = INPATIENT_BASED_CLAIM_CONTEXTS.contains(exactContext);
        BenefitPolicyRule generalInpatientRule = null;
        if (inpatientBasedContext) {
            MedicalCategory inpatientGeneral = categoryRepository.findActiveByCode(GENERAL_INPATIENT_CATEGORY_CODE)
                    .filter(candidate -> !candidate.isDeleted())
                    .orElse(null);
            if (inpatientGeneral != null && !inpatientGeneral.getId().equals(category.getId())) {
                generalInpatientRule = ruleRepository.findBestRuleForClaimContext(
                                request.policyId(), inpatientGeneral.getId(), inpatientGeneral.getParentId(), exactContext)
                        .orElse(null);
            }
        }

        BenefitPolicyRule rule = null;
        boolean generalInpatientFallback = false;
        if (generalInpatientRule != null) {
            // In inpatient-derived claim contexts (إيواء، الحمل والولادة، مضاعفات الحمل)
            // the claim context is the financial boundary. A service can remain
            // classified as diagnostics/outpatient in the catalog, but financially
            // it must consume the same general inpatient bucket as the rest of the
            // claim. Otherwise a standard/open-price service can appear to have a
            // separate ceiling inside the same claim.
            rule = generalInpatientRule;
            generalInpatientFallback = true;
        } else {
            rule = ruleRepository.findBestRuleForClaimContext(
                    request.policyId(), category.getId(), category.getParentId(), exactContext)
                    .orElse(null);
        }
        boolean generalOutpatientFallback = false;
        if (rule == null && "OUTPATIENT".equals(exactContext)) {
            MedicalCategory outpatientGeneral = categoryRepository.findActiveByCode(GENERAL_OUTPATIENT_CATEGORY_CODE)
                    .filter(candidate -> !candidate.isDeleted())
                    .orElse(null);
            if (outpatientGeneral != null && !outpatientGeneral.getId().equals(category.getId())) {
                rule = ruleRepository.findBestRuleForClaimContext(
                                request.policyId(), outpatientGeneral.getId(), outpatientGeneral.getParentId(), exactContext)
                        .orElse(null);
                generalOutpatientFallback = rule != null;
            }
        }
        if (rule == null) {
            return rejected(categoryId, CoverageDecisionSource.NO_BENEFIT_RULE, "NO_BENEFIT_RULE");
        }
        Long matchingCategoryId = rule.getMedicalCategory() != null
                ? rule.getMedicalCategory().getId() : categoryId;
        CoverageDecisionSource source = generalInpatientFallback
                ? CoverageDecisionSource.GENERAL_INPATIENT_RULE
                : generalOutpatientFallback
                ? CoverageDecisionSource.GENERAL_OUTPATIENT_RULE
                : matchingCategoryId.equals(category.getId())
                ? CoverageDecisionSource.EXACT_CATEGORY_RULE
                : CoverageDecisionSource.PARENT_CATEGORY_RULE;
        var limits = bucketLimitService.findApplicable(rule.getId(), request.memberId(), request.serviceDate(),
                context, request.excludeClaimId()).stream().map(limit -> CoverageLimitSnapshot.builder()
                        .bucketId(limit.bucketId()).bucketName(limit.bucketName())
                        .amountLimit(limit.amountLimit()).timesLimit(limit.timesLimit()).daysLimit(limit.daysLimit())
                        .usedAmount(limit.usedAmount()).usedTimes(limit.usedTimes()).usedDays(limit.usedDays())
                        .serviceDayAlreadyUsed(limit.serviceDayAlreadyUsed()).countingMethod(limit.countingMethod())
                        .consumptionBasis(limit.consumptionBasis()).directlyLinked(limit.directlyLinked())
                        .periodStart(limit.periodStart()).periodEnd(limit.periodEnd()).build())
                .toList();
        return CoverageDecision.builder()
                .covered(true)
                .coveragePercent(rule.getEffectiveCoveragePercent())
                .resolvedCategoryId(category.getId())
                .matchingCategoryId(matchingCategoryId)
                .source(source)
                .reasonCode("COVERED")
                .appliedRule(BenefitPolicyRuleResponseDto.fromEntity(rule))
                .limits(limits)
                .build();
    }

    private CoverageDecision rejected(Long categoryId, CoverageDecisionSource source, String reasonCode) {
        return CoverageDecision.builder().covered(false).coveragePercent(0)
                .resolvedCategoryId(categoryId).source(source).reasonCode(reasonCode).build();
    }
}
