package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.dto.CoverageDecisionRequest;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.modules.rbac.entity.User;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for validating coverage using BenefitPolicy rules.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SINGLE SOURCE OF TRUTH FOR COVERAGE DECISIONS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * All claim/pre-authorization processing MUST use this service to determine:
 * - Whether a service is covered
 * - What coverage percentage applies
 * - Whether pre-approval is required (ONLY from BenefitPolicyRule, NOT
 * MedicalService)
 * - Amount limits per service/category
 * 
 * ARCHITECTURAL RULES:
 * 1. Coverage is determined ONLY by BenefitPolicy/BenefitPolicyRule
 * 2. PA requirement comes ONLY from BenefitPolicyRule (NOT
 * MedicalService.requiresPA)
 * 3. Price comes from ProviderContract (NOT MedicalService.basePrice)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * COVERAGE RESOLUTION ALGORITHM (CANONICAL)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * resolveCoverage(policyId, serviceId, categoryId):
 * 1. If exists CATEGORY_RULE for categoryId → return CATEGORY_RULE
 * 2. Else → return NOT_COVERED (fail closed)
 * 
 * Priority: EXACT_CATEGORY_RULE > PARENT_CATEGORY_RULE > NOT_COVERED
 * (Service-level rules removed in V228 — category-only architecture)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitPolicyCoverageService {

    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final MedicalServiceRepository serviceRepository;
    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final AuthorizationService authorizationService;
    private final CoverageDecisionService coverageDecisionService;
    private final com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;

    // ═══════════════════════════════════════════════════════════════════════════
    // ARCHITECTURAL CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Default coverage percent when no rule found and policy default is null */
    private static final int SYSTEM_DEFAULT_COVERAGE_PERCENT = 80;

    /**
     * Default PA requirement when no specific rule exists.
     * FALSE = Services don't require pre-approval by default
     * Pre-approval is only required when explicitly set in BenefitPolicyRule
     */
    private static final boolean DEFAULT_REQUIRES_PA = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // POLICY VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate that the member had an active, effective benefit policy ON THE
     * SERVICE DATE.
     *
     * Resolution goes through MemberPolicyResolver -- the same single answer
     * the eligibility engine uses -- so the same member and the same date can
     * never yield one policy here and a different one there.
     *
     * This method previously read member.getBenefitPolicy() and, when that was
     * null, looked a policy up by serviceDate and then PERSISTED it onto the
     * member (a validation path silently rewriting state), with an extra
     * "pick the latest active policy" guess for internal staff that was also
     * persisted. Processing a single backdated claim for a policy-less member
     * therefore decided that member's stored policy permanently. Resolution is
     * now pure; changing a member's policy is an explicit, audited assignment
     * (MemberPolicyResolver.assignPolicy).
     *
     * @param member      The member
     * @param serviceDate The date of service
     * @throws BusinessRuleException if no valid policy applied on that date
     */
    @Transactional(readOnly = true)
    public void validateMemberHasActivePolicy(Member member, LocalDate serviceDate) {
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, serviceDate).orElse(null);

        if (policy == null) {
            throw new BusinessRuleException(
                    String.format("Member %s has no assigned Benefit Policy. Cannot process claim. (Employer: %s)",
                            member.getFullName(),
                            member.getEmployer() != null ? member.getEmployer().getName() : "None"));
        }

        if (!policy.isActive()) {
            throw new BusinessRuleException(
                    String.format("Member's Benefit Policy '%s' is inactive (soft deleted). Cannot process claim.",
                            policy.getName()));
        }

        if (policy.getStatus() != BenefitPolicyStatus.ACTIVE) {
            throw new BusinessRuleException(
                    String.format(
                            "Member's Benefit Policy '%s' status is %s. Only ACTIVE policies can be used for claims.",
                            policy.getName(), policy.getStatus()));
        }

        if (!policy.isEffectiveOn(serviceDate)) {
            User currentUser = authorizationService.getCurrentUser();
            if (currentUser != null && authorizationService.isInternalStaff(currentUser)) {
                log.warn("⚠️ Backlog Entry: Date {} is outside policy '{}' period [{} to {}]. Staff bypass allowed.",
                        serviceDate, policy.getName(), policy.getStartDate(), policy.getEndDate());
            } else {
                throw new BusinessRuleException(
                        String.format("Member's Benefit Policy '%s' is not effective on %s. Policy period: %s to %s",
                                policy.getName(), serviceDate, policy.getStartDate(), policy.getEndDate()));
            }
        }

        log.debug("✅ Member {} has valid policy '{}' for date {}",
                member.getNationalNumber(), policy.getName(), serviceDate);
    }

    /**
     * Check if member has an active policy (non-throwing)
     */
    public boolean hasActivePolicy(Member member, LocalDate serviceDate) {
        try {
            validateMemberHasActivePolicy(member, serviceDate);
            return true;
        } catch (BusinessRuleException e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SERVICE COVERAGE LOOKUP
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a specific category is covered under the member's policy.
     * This is the PRIMARY method for the new category-direct architecture.
     *
     * @param member     The member
     * @param categoryId The medical category ID
     * @return Coverage info, or empty if not covered
     */
    public Optional<CoverageInfo> getCoverageForCategory(Member member, Long categoryId) {
        return getCoverageForCategory(member, categoryId,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT, LocalDate.now());
    }

    public Optional<CoverageInfo> getCoverageForCategory(Member member, Long categoryId,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType, LocalDate serviceDate) {
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, serviceDate).orElse(null);
        if (policy == null || categoryId == null) {
            return Optional.empty();
        }
        ResolvedCoverage resolved = resolveCoverage(policy.getId(), null, categoryId, null,
                member.getId(), serviceDate, null, toCategoryContext(encounterType), 1.0, null, true);
        if (!resolved.isCovered()) return Optional.empty();
        return Optional.of(CoverageInfo.builder()
                .covered(true)
                .coveragePercent(resolved.getCoveragePercent())
                .amountLimit(resolved.getAmountLimit())
                .timesLimit(resolved.getTimesLimit())
                .requiresPreApproval(resolved.isRequiresPreApproval())
                .waitingPeriodDays(resolved.getWaitingPeriodDays())
                .ruleId(resolved.getRuleId())
                .ruleType(resolved.getSource().name())
                .build());
    }

    /**
     * Check if a specific service is covered under the member's policy.
     * 
     * @param member    The member
     * @param serviceId The medical service ID
     * @return Coverage info, or empty if not covered
     */
    public Optional<CoverageInfo> getCoverageForService(Member member, Long serviceId) {
        return getCoverageForService(member, serviceId, null,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT, LocalDate.now());
    }

    /**
     * Check if a specific service is covered under the member's policy with an
     * optional category override.
     * 
     * @param member             The member
     * @param serviceId          The medical service ID
     * @param categoryOverrideId Optional: Category ID to use instead of service's
     *                           default
     * @return Coverage info, or empty if not covered
     */
    public Optional<CoverageInfo> getCoverageForService(Member member, Long serviceId, Long categoryOverrideId) {
        return getCoverageForService(member, serviceId, categoryOverrideId,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT, LocalDate.now());
    }

    public Optional<CoverageInfo> getCoverageForService(Member member, Long serviceId, Long categoryOverrideId,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType, LocalDate serviceDate) {
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, serviceDate).orElse(null);
        if (policy == null) {
            return Optional.empty();
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return Optional.empty();
        }

        // Use override if provided, otherwise resolve from service
        Long categoryId = (categoryOverrideId != null) ? categoryOverrideId : resolveCategoryIdForCoverage(service);

        ResolvedCoverage resolved = resolveCoverage(policy.getId(), serviceId, categoryId,
                categoryOverrideId, member.getId(), serviceDate, null,
                toCategoryContext(encounterType), 1.0, null, true);
        if (!resolved.isCovered()) {
            return Optional.empty();
        }
        return Optional.of(CoverageInfo.builder()
                .covered(true)
                .coveragePercent(resolved.getCoveragePercent())
                .amountLimit(resolved.getAmountLimit())
                .timesLimit(resolved.getTimesLimit())
                .requiresPreApproval(resolved.isRequiresPreApproval())
                .waitingPeriodDays(resolved.getWaitingPeriodDays())
                .ruleId(resolved.getRuleId())
                .ruleType(resolved.getSource() == null ? null : resolved.getSource().name())
                .serviceName(service.getName())
                .categoryName(null)
                .build());
    }

    /**
     * Check if a service requires pre-approval
     */
    public boolean requiresPreApproval(Member member, Long serviceId) {
        return getCoverageForService(member, serviceId)
                .map(CoverageInfo::isRequiresPreApproval)
                .orElse(false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLAIM VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate coverage for all services in a claim.
     * 
     * @param member       The member making the claim
     * @param serviceItems The services in the claim (simplified input)
     * @param serviceDate  Date of service
     * @return Validation result with coverage breakdown
     */
    public ClaimCoverageResult validateClaimCoverage(
            Member member,
            List<ServiceCoverageInput> serviceItems,
            LocalDate serviceDate) {
        return validateClaimCoverage(member, serviceItems, serviceDate,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT);
    }

    public ClaimCoverageResult validateClaimCoverage(
            Member member,
            List<ServiceCoverageInput> serviceItems,
            LocalDate serviceDate,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType) {

        // First validate policy is active
        validateMemberHasActivePolicy(member, serviceDate);

        // The SERVICE DATE's policy, not the member's current pointer.
        BenefitPolicy policy = memberPolicyResolver.resolveForOrFail(member, serviceDate);
        List<ServiceCoverageResult> serviceResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal totalRequestedAmount = BigDecimal.ZERO;
        BigDecimal totalCoveredAmount = BigDecimal.ZERO;
        BigDecimal totalPatientAmount = BigDecimal.ZERO;

        for (ServiceCoverageInput item : serviceItems) {
            ServiceCoverageResult result = validateServiceCoverageForInput(
                    policy, member.getId(), item, serviceDate, encounterType);
            serviceResults.add(result);

            if (!result.isCovered()) {
                errors.add(String.format("Service '%s' is not covered under policy '%s'",
                        result.getServiceName(), policy.getName()));
            } else {
                if (result.isRequiresPreApproval()) {
                    warnings.add(String.format("Service '%s' requires pre-approval",
                            result.getServiceName()));
                }

                // Calculate amounts
                BigDecimal lineAmount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                totalRequestedAmount = totalRequestedAmount.add(lineAmount);

                BigDecimal covered = lineAmount
                        .multiply(BigDecimal.valueOf(result.getCoveragePercent()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                // Apply amount limit if exists
                if (result.getAmountLimit() != null && covered.compareTo(result.getAmountLimit()) > 0) {
                    covered = result.getAmountLimit();
                    warnings.add(String.format("Service '%s' amount limited to %.2f",
                            result.getServiceName(), result.getAmountLimit()));
                }

                totalCoveredAmount = totalCoveredAmount.add(covered);
                totalPatientAmount = totalPatientAmount.add(lineAmount.subtract(covered));
            }
        }

        return ClaimCoverageResult.builder()
                .valid(errors.isEmpty())
                .policyId(policy.getId())
                .policyName(policy.getName())
                .totalRequestedAmount(totalRequestedAmount)
                .totalCoveredAmount(totalCoveredAmount)
                .totalPatientAmount(totalPatientAmount)
                .defaultCoveragePercent(policy.getDefaultCoveragePercent())
                .serviceResults(serviceResults)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * Validate a single service coverage from input DTO
     */
    private ServiceCoverageResult validateServiceCoverageForInput(BenefitPolicy policy, Long memberId,
            ServiceCoverageInput input, LocalDate serviceDate,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType) {
        Long serviceId = input.getServiceId();
        String serviceName = input.getServiceName() != null ? input.getServiceName() : "Unknown Service";

        if (serviceId == null) {
            return ServiceCoverageResult.builder()
                    .serviceId(null)
                    .serviceName(serviceName)
                    .covered(false)
                    .coveragePercent(0)
                    .reason("الخدمة غير مرتبطة بمعرف طبي أو بند تسعير معتمد")
                    .build();
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return ServiceCoverageResult.builder()
                    .serviceId(serviceId)
                    .serviceName(serviceName)
                    .covered(false)
                    .reason("Service not found in database")
                    .build();
        }

        Long categoryId = resolveCategoryIdForCoverage(service);
        ResolvedCoverage resolved = resolveCoverage(policy.getId(), serviceId, categoryId, null,
                memberId, serviceDate, null, toCategoryContext(encounterType), 1.0, input.getAmount(), true);
        if (!resolved.isCovered()) {
            return ServiceCoverageResult.builder()
                    .serviceId(serviceId)
                    .serviceName(service.getName())
                    .serviceCode(service.getCode())
                    .categoryId(categoryId)
                    .categoryName(null)
                    .covered(false)
                    .reason("No coverage rule found for this service or category")
                    .build();
        }

        return ServiceCoverageResult.builder()
                .serviceId(serviceId)
                .serviceName(service.getName())
                .serviceCode(service.getCode())
                .categoryId(categoryId)
                .categoryName(null)
                .covered(true)
                .coveragePercent(resolved.getCoveragePercent())
                .amountLimit(resolved.getAmountLimit())
                .timesLimit(resolved.getTimesLimit())
                .requiresPreApproval(resolved.isRequiresPreApproval())
                .ruleId(resolved.getRuleId())
                .ruleType(resolved.getSource().name())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUICK CHECKS FOR CLAIM CREATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Quick validation before claim creation.
     * Throws BusinessRuleException if claim cannot be created.
     */
    public void validateCanCreateClaim(Member member, LocalDate serviceDate) {
        validateMemberHasActivePolicy(member, serviceDate);
    }

    /**
     * Get the effective coverage percentage for a service
     * Returns 0 if not covered
     */
    public int getCoveragePercentForService(Member member, Long serviceId) {
        return getCoverageForService(member, serviceId)
                .map(CoverageInfo::getCoveragePercent)
                .orElse(0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AMOUNT LIMIT VALIDATION (Migrated from CoverageValidationService)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate amount against BenefitPolicy limits.
     * This replaces the legacy CoverageValidationService.validateAmountLimits().
     *
     * Validates:
     * - Annual limit per member
     * - Per-member limit on policy
     * - Per-family limit on policy
     *
     * @param member          The member making the claim
     * @param benefitPolicy   The member's BenefitPolicy
     * @param requestedAmount The requested claim amount
     * @param serviceDate     The date of service
     * @throws BusinessRuleException if any limit is exceeded
     * @deprecated use {@link #validateAmountLimits(Member, BenefitPolicy, BigDecimal, LocalDate, Long)}
     *             — this overload cannot exclude an already-persisted claim from the
     *             "previously used" totals, which double-counts the claim being validated
     *             whenever it was saved (even in a non-final status) before this check runs.
     *             Kept only for callers that validate a not-yet-persisted amount.
     */
    @Deprecated
    public void validateAmountLimits(
            Member member,
            BenefitPolicy benefitPolicy,
            BigDecimal requestedAmount,
            LocalDate serviceDate) {
        validateAmountLimits(member, benefitPolicy, requestedAmount, serviceDate, null);
    }

    /**
     * Validate amount against BenefitPolicy limits, excluding a specific claim from the
     * "previously used" aggregation.
     *
     * excludeClaimId MUST be the claim's own id whenever that claim already exists as a
     * row in the database at call time (e.g. finalizeSnapshot runs against an
     * already-saved claim) — otherwise the claim being validated is summed as part of
     * its own prior usage and compared against itself a second time, silently doubling
     * its effective cost against the member/family limit.
     *
     * @param member          The member making the claim
     * @param benefitPolicy   The member's BenefitPolicy
     * @param requestedAmount The requested claim amount
     * @param serviceDate     The date of service
     * @param excludeClaimId  Id of the claim being validated, or null if it has no row yet
     * @throws BusinessRuleException if any limit is exceeded
     */
    public void validateAmountLimits(
            Member member,
            BenefitPolicy benefitPolicy,
            BigDecimal requestedAmount,
            LocalDate serviceDate,
            Long excludeClaimId) {

        if (benefitPolicy == null) {
            throw new BusinessRuleException("Member has no BenefitPolicy assigned");
        }

        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No amount to validate
        }

        log.debug("🔍 Validating amount limits for member {} amount {} on {} (excludeClaimId={})",
                member.getId(), requestedAmount, serviceDate, excludeClaimId);

        // Check annual limit from BenefitPolicy
        BigDecimal annualLimit = benefitPolicy.getAnnualLimit();
        if (annualLimit != null && annualLimit.compareTo(BigDecimal.ZERO) > 0) {
            // WAAD-FIN-1.0 S4: the ceiling tracks limit consumption
            // (settlement value), not approvedAmount -- see getLimitConsumedForYear's
            // javadoc. Callers must pass an amount on the same axis (see
            // ClaimFinancialSnapshotService, which passes the claim's actual
            // limitConsumption total, not its approvedAmount).
            BigDecimal usedAmount = getLimitConsumedForYear(member.getId(), serviceDate.getYear(), excludeClaimId);
            BigDecimal remainingLimit = annualLimit.subtract(usedAmount);

            if (requestedAmount.compareTo(remainingLimit) > 0) {
                log.warn("❌ Annual limit exceeded: requested={}, remaining={}, annual={}",
                        requestedAmount, remainingLimit, annualLimit);
                throw new BusinessRuleException(
                        String.format(
                                "المبلغ المطلوب (%.2f) يتجاوز الحد السنوي المتبقي (%.2f). الحد السنوي: %.2f، المستخدم: %.2f",
                                requestedAmount, remainingLimit, annualLimit, usedAmount));
            }
        }

        // Check per-member limit
        BigDecimal perMemberLimit = benefitPolicy.getPerMemberLimit();
        if (perMemberLimit != null && perMemberLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalUsed = calculateTotalUsedForMember(member.getId(), excludeClaimId);
            BigDecimal remaining = perMemberLimit.subtract(totalUsed);

            if (requestedAmount.compareTo(remaining) > 0) {
                log.warn("❌ Per-member limit exceeded: requested={}, remaining={}", requestedAmount, remaining);
                throw new BusinessRuleException(
                        String.format("المبلغ المطلوب (%.2f) يتجاوز حد العضو المتبقي (%.2f)",
                                requestedAmount, remaining));
            }
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PHASE 2.2: PER-FAMILY LIMIT VALIDATION
        // ═══════════════════════════════════════════════════════════════════════════
        BigDecimal perFamilyLimit = benefitPolicy.getPerFamilyLimit();
        if (perFamilyLimit != null && perFamilyLimit.compareTo(BigDecimal.ZERO) > 0) {
            Long principalId = member.getPrincipalMember().getId();
            BigDecimal familyUsed = calculateFamilyUsedAmountForYear(principalId, serviceDate.getYear(), excludeClaimId);
            BigDecimal remainingFamily = perFamilyLimit.subtract(familyUsed);

            if (requestedAmount.compareTo(remainingFamily) > 0) {
                log.warn("❌ Per-family limit exceeded for family {}: requested={}, remaining={}, familyLimit={}",
                        principalId, requestedAmount, remainingFamily, perFamilyLimit);
                throw new BusinessRuleException(
                        String.format(
                                "المبلغ المطلوب (%.2f) يتجاوز حد العائلة المتبقي (%.2f). حد العائلة: %.2f، المستخدم: %.2f",
                                requestedAmount, remainingFamily, perFamilyLimit, familyUsed));
            }
        }

        log.debug("✅ Amount limits validation passed for member {}", member.getId());
    }

    /**
     * Validate waiting periods for a member against
     * BenefitPolicy/BenefitPolicyRule.
     * This replaces the legacy CoverageValidationService.validateWaitingPeriods().
     * 
     * Waiting period logic:
     * 1. Check policy-level defaultWaitingPeriodDays
     * 2. For each claim line, check BenefitPolicyRule.waitingPeriodDays (overrides
     * default)
     * 
     * @param member        The member making the claim
     * @param benefitPolicy The member's BenefitPolicy
     * @param claimLines    Optional list of claim lines to validate per-service
     *                      waiting
     * @param serviceDate   The date of service
     * @throws BusinessRuleException if waiting period not satisfied
     */
    public void validateWaitingPeriods(
            Member member,
            BenefitPolicy benefitPolicy,
            List<ClaimLine> claimLines,
            LocalDate serviceDate) {

        if (benefitPolicy == null) {
            return; // No policy to validate
        }

        LocalDate memberStartDate = member.getStartDate();
        if (memberStartDate == null) {
            memberStartDate = member.getJoinDate();
        }
        if (memberStartDate == null) {
            log.debug("Member {} has no start/join date, skipping waiting period check", member.getId());
            return; // Cannot validate without dates
        }

        long daysSinceEnrollment = java.time.temporal.ChronoUnit.DAYS.between(memberStartDate, serviceDate);

        // Check policy-level default waiting period
        Integer defaultWaiting = benefitPolicy.getDefaultWaitingPeriodDays();
        if (defaultWaiting != null && defaultWaiting > 0) {
            if (daysSinceEnrollment < defaultWaiting) {
                LocalDate eligibleDate = memberStartDate.plusDays(defaultWaiting);
                throw new BusinessRuleException(
                        String.format(
                                "فترة الانتظار العامة لم تكتمل. العضو سيكون مؤهلاً للتغطية من %s (مطلوب %d يوم، مضى %d يوم)",
                                eligibleDate, defaultWaiting, daysSinceEnrollment));
            }
        }

        // Check per-service/category waiting periods from rules
        if (claimLines != null && !claimLines.isEmpty()) {
            for (ClaimLine line : claimLines) {
                validateWaitingPeriodForClaimLine(benefitPolicy, line, memberStartDate, serviceDate,
                        daysSinceEnrollment);
            }
        }

        log.debug("✅ Waiting period validation passed for member {}", member.getId());
    }

    /**
     * Validate waiting period for a specific claim line.
     * Uses the line's serviceCategoryId directly (no MedicalService lookup needed).
     */
    private void validateWaitingPeriodForClaimLine(
            BenefitPolicy benefitPolicy,
            ClaimLine line,
            LocalDate memberStartDate,
            LocalDate serviceDate,
            long daysSinceEnrollment) {

        Long categoryId = line.getServiceCategoryId();
        if (categoryId == null) {
            log.debug("ClaimLine has no serviceCategoryId, skipping waiting period check for line {}", line.getId());
            return;
        }

        var encounterType = line.getClaim() != null ? line.getClaim().getEncounterType()
                : com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT;
        ResolvedCoverage resolved = resolveCoverage(benefitPolicy.getId(), null, categoryId, null,
                line.getClaim() != null && line.getClaim().getMember() != null
                        ? line.getClaim().getMember().getId() : null,
                serviceDate, line.getClaim() != null ? line.getClaim().getId() : null,
                toCategoryContext(encounterType), 1.0, null, true);

        if (resolved.isCovered()) {
            Integer ruleWaitingDays = resolved.getWaitingPeriodDays();

            if (ruleWaitingDays != null && ruleWaitingDays > 0 && daysSinceEnrollment < ruleWaitingDays) {
                LocalDate eligibleDate = memberStartDate.plusDays(ruleWaitingDays);
                String serviceName = line.getServiceName() != null ? line.getServiceName() : line.getServiceCode();
                throw new BusinessRuleException(
                        String.format("فترة الانتظار للخدمة '%s' لم تكتمل. العضو سيكون مؤهلاً من %s (مطلوب %d يوم)",
                                serviceName, eligibleDate, ruleWaitingDays));
            }
        }
    }

    /**
     * Validate that a service is covered under the BenefitPolicy.
     * Checks if a BenefitPolicyRule exists for the service or its category.
     * 
     * @param serviceId     The medical service ID
     * @param benefitPolicy The BenefitPolicy to check
     * @throws BusinessRuleException if service is not covered
     */
    public void validateServiceCoverage(Long serviceId, BenefitPolicy benefitPolicy) {
        if (serviceId == null || benefitPolicy == null) {
            return; // Nothing to validate
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            throw new BusinessRuleException("الخدمة الطبية غير موجودة: " + serviceId);
        }

        Long categoryId = resolveCategoryIdForCoverage(service);

        ResolvedCoverage resolved = resolveCoverage(benefitPolicy.getId(), serviceId, categoryId, null,
                null, LocalDate.now(), null, CategoryContext.OUTPATIENT, 1.0, null, true);

        if (!resolved.isCovered()) {
            String serviceName = service.getName() != null ? service.getName() : service.getName();
            throw new BusinessRuleException(
                    String.format("الخدمة '%s' غير مغطاة في وثيقة المزايا '%s'",
                            serviceName, benefitPolicy.getName()));
        }

        log.debug("✅ Service {} is covered under policy {}", serviceId, benefitPolicy.getName());
    }

    /**
     * Validate service coverage by service code (legacy support).
     * 
     * @param serviceCode   The service code
     * @param benefitPolicy The BenefitPolicy to check
     * @throws BusinessRuleException if service is not covered
     */
    public void validateServiceCoverageByCode(String serviceCode, BenefitPolicy benefitPolicy) {
        if (serviceCode == null || serviceCode.isBlank() || benefitPolicy == null) {
            return;
        }

        // Try to find service by code
        MedicalService service = serviceRepository.findByCode(serviceCode).orElse(null);
        if (service != null) {
            validateServiceCoverage(service.getId(), benefitPolicy);
        } else {
            log.warn("Service code {} not found in database, skipping coverage check", serviceCode);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS FOR AMOUNT CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * What the member's annual benefit limit has consumed this year -- the
     * single source of truth for "used against the ceiling", for exactly one
     * member. WAAD-FIN-1.0 S4: a limit consumes settlement value
     * ({@code ClaimLine.limitConsumption}), never what the insurer ultimately
     * paid ({@code Claim.approvedAmount}) -- those differ by construction
     * whenever a limit, coverage split, contract discount, or rejection
     * applied. Every caller that needs "how much of the annual limit is
     * used" (the approval gate, the member window, family eligibility) must
     * go through this method or {@link #getLimitConsumedForYear(Collection, int, Long)}
     * -- never re-derive it from approvedAmount.
     *
     * @param memberId      the member whose consumption is being read
     * @param year          the benefit year (annual limits reset on calendar years)
     * @param excludeClaimId the claim currently being validated, if it already has a
     *                       row (so it is not counted against its own prior usage);
     *                       null when no such row exists yet
     */
    public BigDecimal getLimitConsumedForYear(Long memberId, int year, Long excludeClaimId) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        return claimRepository.sumLimitConsumptionByMemberAndPeriodExcludingClaim(
                memberId, yearStart, yearEnd, excludeClaimId);
    }

    /**
     * Bulk counterpart of {@link #getLimitConsumedForYear(Long, int, Long)} --
     * one query for an entire family instead of one query per member, so
     * reading a principal-plus-dependents window costs O(1) queries
     * regardless of family size. Members with no consumption this year are
     * present in the result with {@link BigDecimal#ZERO}, never absent --
     * callers must not treat a missing key as "unknown" and a present zero
     * key as "nothing used" differently.
     *
     * @param memberIds     every member whose consumption is being read together
     * @param year          the benefit year (annual limits reset on calendar years)
     * @param excludeClaimId the claim currently being validated, if it already has a
     *                       row; null when no such row exists yet
     */
    public Map<Long, BigDecimal> getLimitConsumedForYear(Collection<Long> memberIds, int year, Long excludeClaimId) {
        Map<Long, BigDecimal> byMember = new HashMap<>();
        for (Long memberId : memberIds) {
            byMember.put(memberId, BigDecimal.ZERO);
        }
        if (memberIds.isEmpty()) {
            return byMember;
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        for (var row : claimRepository.sumLimitConsumptionByMembersAndPeriodExcludingClaim(
                memberIds, yearStart, yearEnd, excludeClaimId)) {
            byMember.put(row.getMemberId(), row.getConsumedAmount());
        }
        return byMember;
    }

    /**
     * Calculate used amount for a whole family in a specific year using DB
     * aggregation.
     */
    private BigDecimal calculateFamilyUsedAmountForYear(Long principalId, int year, Long excludeClaimId) {
        List<com.waad.tba.modules.claim.entity.ClaimStatus> validStatuses = List.of(
                com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED,
                com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED,
                com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED);
        return claimRepository.sumApprovedAmountByFamilyAndYear(principalId, year, validStatuses, excludeClaimId);
    }

    /**
     * Calculate total approved amount for a member across all years (lifetime).
     * Uses DB aggregation — avoids loading all claims into memory.
     */
    private BigDecimal calculateTotalUsedForMember(Long memberId, Long excludeClaimId) {
        List<com.waad.tba.modules.claim.entity.ClaimStatus> validStatuses = List.of(
                com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED,
                com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED,
                com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED);
        BigDecimal total = claimRepository.sumApprovedAmountByMember(memberId, validStatuses, excludeClaimId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get remaining coverage for a member (for UI display).
     * Uses member's direct benefit policy only. For employer-fallback scenarios,
     * use getRemainingCoverage(BenefitPolicy, Long, LocalDate) instead.
     */
    public BigDecimal getRemainingCoverage(Member member, LocalDate asOfDate) {
        // "Remaining as of a date" must be measured against the policy that
        // applied on that date -- reading the current pointer would restate a
        // past balance using today's annual limit.
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, asOfDate).orElse(null);
        return getRemainingCoverage(policy, member.getId(), asOfDate);
    }

    /**
     * Get remaining coverage given an already-resolved policy (supports
     * employer-level fallback). WAAD-FIN-1.0 S4: "remaining" is
     * {@code annualLimit - consumed}, where consumed is limit consumption
     * (settlement value), never approvedAmount -- see
     * {@link #getLimitConsumedForYear(Long, int, Long)}.
     */
    public BigDecimal getRemainingCoverage(BenefitPolicy policy, Long memberId, LocalDate asOfDate) {
        if (policy == null) {
            return null;
        }

        BigDecimal annualLimit = policy.getAnnualLimit();
        if (annualLimit == null || annualLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // Unlimited or not configured
        }

        BigDecimal used = getLimitConsumedForYear(memberId, asOfDate.getYear(), null);
        return annualLimit.subtract(used).max(BigDecimal.ZERO);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage information for a single service
     */
    @Data
    @Builder
    public static class CoverageInfo {
        private boolean covered;
        private int coveragePercent;
        private BigDecimal amountLimit;
        private Integer timesLimit;
        private boolean requiresPreApproval;
        private Integer waitingPeriodDays;
        private Long ruleId;
        private String ruleType;
        private String serviceName;
        private String categoryName;
    }

    /**
     * Result of claim coverage validation
     */
    @Data
    @Builder
    public static class ClaimCoverageResult {
        private boolean valid;
        private Long policyId;
        private String policyName;
        private BigDecimal totalRequestedAmount;
        private BigDecimal totalCoveredAmount;
        private BigDecimal totalPatientAmount;
        private Integer defaultCoveragePercent;
        private List<ServiceCoverageResult> serviceResults;
        private List<String> errors;
        private List<String> warnings;

        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        public String getErrorSummary() {
            if (errors == null || errors.isEmpty())
                return null;
            return String.join("; ", errors);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNIFIED COVERAGE RESOLUTION (CANONICAL ALGORITHM)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * CANONICAL coverage resolution algorithm.
     * 
     * This is the SINGLE implementation for resolving coverage.
     * All other methods should delegate to this.
     * 
     * Algorithm:
     * 1. If SERVICE_RULE exists → return SERVICE_RULE
     * 2. Else if CATEGORY_RULE exists → return CATEGORY_RULE
     * 3. Else → return NO_BENEFIT_RULE with zero coverage
     * 
     * @param policyId   The benefit policy ID
     * @param serviceId  The medical service ID
     * @param categoryId The medical category ID (from service)
     *                   Priority: EXACT_CATEGORY_RULE > PARENT_CATEGORY_RULE > NO_BENEFIT_RULE
     */
    private CategoryContext toCategoryContext(
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType) {
        var effective = encounterType != null
                ? encounterType
                : com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT;
        return CategoryContext.valueOf(effective.name());
    }

    public ResolvedCoverage resolveCoverage(
            Long policyId,
            Long serviceId,
            Long serviceCategoryId,
            Long overrideCategoryId,
            Long memberId,
            LocalDate serviceDate,
            Long claimIdToExclude) {
        return resolveCoverage(policyId, serviceId, serviceCategoryId, overrideCategoryId, memberId, serviceDate, claimIdToExclude, null, null, null, false);
    }

    public ResolvedCoverage resolveCoverage(
            Long policyId,
            Long serviceId,
            Long serviceCategoryId,
            Long overrideCategoryId,
            Long memberId,
            LocalDate serviceDate,
            Long claimIdToExclude,
            CategoryContext requestedEncounterContext,
            Double classificationConfidence,
            BigDecimal requestedAmount,
            boolean dryRun) {

        log.debug(
                "🔍 Resolving coverage: policyId={}, serviceId={}, serviceCat={}, overrideCat={}, memberId={}, date={}, dryRun={}",
                policyId, serviceId, serviceCategoryId, overrideCategoryId, memberId, serviceDate, dryRun);
        var decision = coverageDecisionService.resolve(CoverageDecisionRequest.builder()
                .policyId(policyId).serviceId(serviceId).serviceCategoryId(serviceCategoryId)
                .overrideCategoryId(overrideCategoryId).memberId(memberId).serviceDate(serviceDate)
                .excludeClaimId(claimIdToExclude)
                .encounterType(requestedEncounterContext == null
                        ? com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT
                        : com.waad.tba.modules.providercontract.enums.EncounterType.valueOf(requestedEncounterContext.name()))
                .classificationConfidence(classificationConfidence).requestedAmount(requestedAmount).build());
        if (!decision.covered()) {
            return ResolvedCoverage.builder().covered(false).coveragePercent(0)
                    .matchingCategoryId(decision.matchingCategoryId())
                    .source(CoverageSource.valueOf(decision.source().name())).build();
        }
        var rule = decision.appliedRule();
        var amountLimit = decision.limitsOrEmpty().stream()
                .filter(com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot::directlyLinked)
                .filter(limit -> limit.amountLimit() != null)
                .min(java.util.Comparator.comparing(
                        com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot::amountLimit))
                .orElse(null);
        var timesLimit = decision.limitsOrEmpty().stream()
                .filter(com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot::directlyLinked)
                .filter(limit -> limit.timesLimit() != null)
                .min(java.util.Comparator.comparing(
                        com.waad.tba.modules.benefitpolicy.dto.CoverageLimitSnapshot::timesLimit))
                .orElse(null);
        ResolvedCoverage resolved = ResolvedCoverage.builder().covered(true)
                .coveragePercent(decision.coveragePercent()).matchingCategoryId(decision.matchingCategoryId())
                .amountLimit(amountLimit == null ? null : amountLimit.amountLimit())
                .usedAmount(amountLimit == null ? null : amountLimit.usedAmount())
                .remainingAmount(amountLimit == null ? null
                        : amountLimit.amountLimit().subtract(amountLimit.usedAmount()).max(BigDecimal.ZERO))
                .timesLimit(timesLimit == null ? null : timesLimit.timesLimit())
                .requiresPreApproval(rule.isRequiresPreApproval()).waitingPeriodDays(rule.getWaitingPeriodDays())
                .ruleId(rule.getId()).source(CoverageSource.valueOf(decision.source().name())).build();
        return resolved;
    }

    /**
     * Check if a service requires pre-approval.
     * 
     * ARCHITECTURAL RULE: PA requirement comes ONLY from BenefitPolicyRule,
     * NOT from MedicalService.requiresPA (which is deprecated).
     * 
     * @param member    The member
     * @param serviceId The service ID
     * @return true if PA is required
     */
    public boolean requiresPreApprovalFromPolicy(Member member, Long serviceId) {
        LocalDate asOfDate = LocalDate.now();
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, asOfDate).orElse(null);
        if (policy == null) {
            return DEFAULT_REQUIRES_PA;
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return DEFAULT_REQUIRES_PA;
        }

        ResolvedCoverage coverage = resolveCoverage(policy.getId(), serviceId, resolveCategoryIdForCoverage(service),
                null, member.getId(), asOfDate, null);
        if (coverage == null) {
            return DEFAULT_REQUIRES_PA;
        }

        return coverage.isRequiresPreApproval();
    }

    /**
     * Get effective coverage percent for a member/service combination.
     * Uses the canonical resolution algorithm.
     */
    public int getEffectiveCoveragePercent(Member member, Long serviceId) {
        LocalDate asOfDate = LocalDate.now();
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, asOfDate).orElse(null);
        if (policy == null) {
            return 0;
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return 0;
        }

        ResolvedCoverage coverage = resolveCoverage(policy.getId(), serviceId, resolveCategoryIdForCoverage(service),
                null, member.getId(), asOfDate, null);
        if (coverage == null || !coverage.isCovered()) {
            return 0;
        }

        return coverage.getCoveragePercent();
    }

    /**
     * Batch preload coverage percentages for multiple services.
     * PERFORMANCE OPTIMIZATION: Avoids N+1 queries by loading all rules in a single
     * query.
     * 
     * @param member     The member
     * @param serviceIds List of service IDs to resolve coverage for
     * @return Map of serviceId → coverage percentage (0 if not covered)
     */
    public java.util.Map<Long, Integer> batchGetCoveragePercents(Member member, java.util.List<Long> serviceIds) {
        java.util.Map<Long, Integer> result = new java.util.HashMap<>();

        LocalDate asOfDate = LocalDate.now();
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, asOfDate).orElse(null);
        if (policy == null || serviceIds == null || serviceIds.isEmpty()) {
            // Return all zeros
            for (Long id : serviceIds != null ? serviceIds : java.util.Collections.<Long>emptyList()) {
                result.put(id, 0);
            }
            return result;
        }

        Long policyId = policy.getId();
        java.util.List<MedicalService> services = serviceRepository.findAllById(serviceIds);
        java.util.Map<Long, MedicalService> serviceMap = services.stream()
                .collect(java.util.stream.Collectors.toMap(MedicalService::getId, service -> service));
        for (Long serviceId : serviceIds) {
            MedicalService service = serviceMap.get(serviceId);
            Long categoryId = service != null ? resolveCategoryIdForCoverage(service) : null;
            ResolvedCoverage resolved = resolveCoverage(policyId, serviceId, categoryId, null,
                    member.getId(), asOfDate, null, CategoryContext.OUTPATIENT, 1.0, null, true);
            result.put(serviceId, resolved.isCovered() ? resolved.getCoveragePercent() : 0);
        }

        log.info("📊 Batch coverage resolved for {} services in policy {}", serviceIds.size(), policyId);
        return result;
    }

    /**
     * Get effective coverage percent for a member/category combination.
     * Category-based resolution (replaces service-based lookup after V229).
     */
    public int getEffectiveCoveragePercentByCategory(Member member, Long categoryId) {
        return getEffectiveCoveragePercentByCategory(member, categoryId,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT, LocalDate.now());
    }

    public int getEffectiveCoveragePercentByCategory(Member member, Long categoryId,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType, LocalDate serviceDate) {
        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, serviceDate).orElse(null);
        if (policy == null || categoryId == null) {
            return 0;
        }

        return getCoverageForCategory(member, categoryId, encounterType, serviceDate)
                .map(CoverageInfo::getCoveragePercent).orElse(0);
    }

    /**
     * Batch preload coverage percentages for multiple categories.
     * Category-based resolution (replaces service-based batchGetCoveragePercents
     * after V229).
     *
     * @param member      The member
     * @param categoryIds List of category IDs to resolve coverage for
     * @return Map of categoryId → coverage percentage
     */
    public java.util.Map<Long, Integer> batchGetCoveragePercentsByCategory(Member member,
            java.util.List<Long> categoryIds) {
        return batchGetCoveragePercentsByCategory(member, categoryIds,
                com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT, LocalDate.now());
    }

    public java.util.Map<Long, Integer> batchGetCoveragePercentsByCategory(Member member,
            java.util.List<Long> categoryIds,
            com.waad.tba.modules.providercontract.enums.EncounterType encounterType,
            LocalDate serviceDate) {
        java.util.Map<Long, Integer> result = new java.util.HashMap<>();

        BenefitPolicy policy = memberPolicyResolver.resolveFor(member, serviceDate).orElse(null);
        if (policy == null || categoryIds == null || categoryIds.isEmpty()) {
            if (categoryIds != null)
                categoryIds.forEach(id -> result.put(id, 0));
            return result;
        }

        for (Long categoryId : categoryIds) {
            result.put(categoryId, getCoverageForCategory(member, categoryId, encounterType, serviceDate)
                    .map(CoverageInfo::getCoveragePercent).orElse(0));
        }

        log.info("📊 Batch category coverage resolved for {} categories in policy {}", categoryIds.size(), policy.getId());
        return result;
    }

    /**
     * Resolved coverage result from the canonical algorithm
     */
    @Data
    @Builder
    public static class ResolvedCoverage {
        private boolean covered;
        private int coveragePercent;
        private BigDecimal amountLimit;
        private Long matchingCategoryId; // Category that matched the rule
        private BigDecimal usedAmount;
        private BigDecimal remainingAmount;
        private Integer timesLimit;
        private boolean requiresPreApproval;
        private Integer waitingPeriodDays;
        private Long ruleId;
        private CoverageSource source;

        public static ResolvedCoverage fromRule(BenefitPolicyRule rule, CoverageSource source,
                Long matchingCategoryId) {
            return ResolvedCoverage.builder()
                    .covered(true)
                    .coveragePercent(rule.getEffectiveCoveragePercent())
                    .amountLimit(null)
                    .timesLimit(null)
                    .requiresPreApproval(rule.isRequiresPreApproval())
                    .waitingPeriodDays(rule.getWaitingPeriodDays())
                    .ruleId(rule.getId())
                    .matchingCategoryId(matchingCategoryId)
                    .source(source)
                    .build();
        }
    }

    /**
     * Coverage source enum for tracking where coverage was resolved from
     */
    public enum CoverageSource {
        SERVICE_RULE, // Specific rule for this service
        CATEGORY_RULE, // Rule for the service's category
        EXCLUDED_CATEGORY, // Category is explicitly excluded
        
        // Simulation specific additions
        EXACT_CATEGORY_RULE,
        PARENT_CATEGORY_RULE,
        NO_BENEFIT_RULE,
        INVALID_CATEGORY,
        CONTEXT_MISMATCH,
        LOW_CONFIDENCE,
        PRICE_ZERO,
        CONFLICTING_RULES,
        PRE_APPROVAL_REQUIRED,
        LIMIT_APPLIED,
        PARTIAL_COVERAGE
    }

    /**
     * Coverage result for a single service in a claim
     */
    @Data
    @Builder
    public static class ServiceCoverageResult {
        private Long serviceId;
        private String serviceName;
        private String serviceCode;
        private Long categoryId;
        private String categoryName;
        private boolean covered;
        private int coveragePercent;
        private BigDecimal amountLimit;
        private Integer timesLimit;
        private boolean requiresPreApproval;
        private Long ruleId;
        private String ruleType;
        private String reason; // Reason for not covered
    }

    /**
     * Input DTO for service coverage validation
     * Used to pass service details from ClaimLine or other sources
     */
    @Data
    @Builder
    public static class ServiceCoverageInput {
        private Long serviceId;
        private String serviceName;
        private BigDecimal amount;

        /**
         * Create from ClaimLine fields
         */
        public static ServiceCoverageInput fromClaimLine(Long serviceId, String description, BigDecimal totalPrice) {
            return ServiceCoverageInput.builder()
                    .serviceId(serviceId)
                    .serviceName(description)
                    .amount(totalPrice)
                    .build();
        }
    }

    private Long resolveCategoryIdForCoverage(MedicalService service) {
        if (service == null) {
            return null;
        }
        return service.getCategoryId();
    }

}
