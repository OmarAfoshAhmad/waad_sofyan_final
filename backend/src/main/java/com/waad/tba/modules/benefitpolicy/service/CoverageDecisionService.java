package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.dto.*;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
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
        if (category.getContexts() != null && !category.getContexts().isEmpty()) {
            CategoryContext categoryContext = CategoryContext.valueOf(context.name());
            if (!category.getContexts().contains(CategoryContext.ANY)
                    && !category.getContexts().contains(categoryContext)) {
                return rejected(categoryId, CoverageDecisionSource.CONTEXT_MISMATCH, "CONTEXT_MISMATCH");
            }
        }

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
            if (definition == null || !definition.isActive()
                    || (definition.getBaseEncounterType() != EncounterType.ANY
                    && definition.getBaseEncounterType() != context)) {
                return rejected(categoryId, CoverageDecisionSource.CONTEXT_MISMATCH, "CLAIM_CONTEXT_MISMATCH");
            }
        }
        BenefitPolicyRule rule = ruleRepository.findBestRuleForClaimContext(
                request.policyId(), category.getId(), category.getParentId(), exactContext)
                .orElse(null);
        if (rule == null) {
            return rejected(categoryId, CoverageDecisionSource.NO_BENEFIT_RULE, "NO_BENEFIT_RULE");
        }
        Long matchingCategoryId = rule.getMedicalCategory() != null
                ? rule.getMedicalCategory().getId() : categoryId;
        CoverageDecisionSource source = matchingCategoryId.equals(category.getId())
                ? CoverageDecisionSource.EXACT_CATEGORY_RULE
                : CoverageDecisionSource.PARENT_CATEGORY_RULE;
        var limits = bucketLimitService.findApplicable(rule.getId(), request.memberId(), request.serviceDate(),
                context, request.excludeClaimId()).stream().map(limit -> CoverageLimitSnapshot.builder()
                        .bucketId(limit.bucketId()).bucketName(limit.bucketName())
                        .amountLimit(limit.amountLimit()).timesLimit(limit.timesLimit()).daysLimit(limit.daysLimit())
                        .usedAmount(limit.usedAmount()).usedTimes(limit.usedTimes()).usedDays(limit.usedDays())
                        .serviceDayAlreadyUsed(limit.serviceDayAlreadyUsed()).countingMethod(limit.countingMethod())
                        .consumptionBasis(limit.consumptionBasis()).directlyLinked(limit.directlyLinked()).build())
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
