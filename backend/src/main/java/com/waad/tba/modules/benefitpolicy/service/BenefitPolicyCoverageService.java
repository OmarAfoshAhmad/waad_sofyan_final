package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
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
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
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
import java.util.List;
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
 * 1. If exists SERVICE_RULE for serviceId → return SERVICE_RULE
 * 2. Else if exists CATEGORY_RULE for categoryId → return CATEGORY_RULE
 * 3. Else → return POLICY_DEFAULT (or NOT_COVERED)
 * 
 * Priority: SERVICE_RULE > CATEGORY_RULE > POLICY_DEFAULT
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitPolicyCoverageService {

    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final MedicalServiceRepository serviceRepository;
    private final MedicalServiceCategoryRepository serviceCategoryRepository;
    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final AuthorizationService authorizationService;

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
     * Validate that member has an active, effective benefit policy.
     * 
     * AUTO-RESOLVE (Permanent Fix - 2026-02-24):
     * If member has no assigned policy, automatically resolve from employer's
     * active effective policy and persist it. This handles Excel-imported members
     * who may not have had their policy assigned during import.
     * 
     * @param member      The member
     * @param serviceDate The date of service
     * @throws BusinessRuleException if no valid policy exists
     */
    @Transactional
    public void validateMemberHasActivePolicy(Member member, LocalDate serviceDate) {
        BenefitPolicy policy = member.getBenefitPolicy();

        // ═══════════════════════════════════════════════════════════════════════
        // AUTO-RESOLVE: If member has no policy, try to find from employer
        // ═══════════════════════════════════════════════════════════════════════
        if (policy == null && member.getEmployer() != null) {
            log.info("🔄 Member {} has no policy assigned. Auto-resolving from employer '{}'...",
                    member.getFullName(), member.getEmployer().getName());

            var resolvedOpt = policyRepository
                    .findActiveEffectivePolicyForEmployer(member.getEmployer().getId(), serviceDate);

            // Fallback for internal staff: Try finding ANY active policy regardless of date
            // for backlog entry
            if (resolvedOpt.isEmpty()) {
                User currentUser = authorizationService.getCurrentUser();
                if (currentUser != null && authorizationService.isInternalStaff(currentUser)) {
                    log.info(
                            "🔍 No date-matched policy found for backlog. Searching for any active policy for employer...");
                    List<BenefitPolicy> allActive = policyRepository.findByEmployerIdAndStatusAndActiveTrue(
                            member.getEmployer().getId(), BenefitPolicyStatus.ACTIVE);
                    if (!allActive.isEmpty()) {
                        // Pick the latest created one
                        policy = allActive.get(allActive.size() - 1);
                        log.info("✅ Best-effort resolution: using latest active policy '{}' for backlog entry",
                                policy.getName());
                    }
                }
            } else {
                policy = resolvedOpt.get();
            }

            if (policy != null) {
                member.setBenefitPolicy(policy);
                memberRepository.save(member);
                log.info("✅ Auto-resolved and saved policy '{}' for member '{}'",
                        policy.getName(), member.getFullName());
            }
        }

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
     * Check if a specific service is covered under the member's policy.
     * 
     * @param member    The member
     * @param serviceId The medical service ID
     * @return Coverage info, or empty if not covered
     */
    public Optional<CoverageInfo> getCoverageForService(Member member, Long serviceId) {
        return getCoverageForService(member, serviceId, null);
    }

    /**
     * Check if a specific service is covered under the member's policy with an optional category override.
     * 
     * @param member             The member
     * @param serviceId          The medical service ID
     * @param categoryOverrideId Optional: Category ID to use instead of service's default
     * @return Coverage info, or empty if not covered
     */
    public Optional<CoverageInfo> getCoverageForService(Member member, Long serviceId, Long categoryOverrideId) {
        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy == null) {
            return Optional.empty();
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return Optional.empty();
        }

        // Use override if provided, otherwise resolve from service
        Long categoryId = (categoryOverrideId != null) ? categoryOverrideId : resolveCategoryIdForCoverage(service);
        
        // Resolve parent category for hierarchical rule lookup
        Long parentCategoryId = null;
        if (categoryId != null) {
            parentCategoryId = categoryRepository.findById(categoryId)
                    .map(MedicalCategory::getParentId)
                    .orElse(null);
        }

        Optional<BenefitPolicyRule> ruleOpt = ruleRepository.findBestRuleForService(
                policy.getId(), serviceId, categoryId, parentCategoryId);

        if (ruleOpt.isEmpty()) {
            log.debug("⚠️ No specific rule found for service {} in policy {}. Falling back to default: {}%", 
                    serviceId, policy.getName(), policy.getDefaultCoveragePercent());

            return Optional.of(CoverageInfo.builder()
                    .covered(true)
                    .coveragePercent(policy.getDefaultCoveragePercent())
                    .requiresPreApproval(false)
                    .ruleType("POLICY_DEFAULT")
                    .serviceName(service.getName())
                    .build());
        }

        BenefitPolicyRule rule = ruleOpt.get();

        return Optional.of(CoverageInfo.builder()
                .covered(true)
                .coveragePercent(rule.getEffectiveCoveragePercent())
                .amountLimit(rule.getAmountLimit())
                .timesLimit(rule.getTimesLimit())
                .requiresPreApproval(rule.isRequiresPreApproval())
                .waitingPeriodDays(rule.getWaitingPeriodDays())
                .ruleId(rule.getId())
                .ruleType(rule.isCategoryRule() ? "CATEGORY" : "SERVICE")
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

        // First validate policy is active
        validateMemberHasActivePolicy(member, serviceDate);

        BenefitPolicy policy = member.getBenefitPolicy();
        List<ServiceCoverageResult> serviceResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal totalRequestedAmount = BigDecimal.ZERO;
        BigDecimal totalCoveredAmount = BigDecimal.ZERO;
        BigDecimal totalPatientAmount = BigDecimal.ZERO;

        for (ServiceCoverageInput item : serviceItems) {
            ServiceCoverageResult result = validateServiceCoverageForInput(policy, item);
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
    private ServiceCoverageResult validateServiceCoverageForInput(BenefitPolicy policy, ServiceCoverageInput input) {
        Long serviceId = input.getServiceId();
        String serviceName = input.getServiceName() != null ? input.getServiceName() : "Unknown Service";

        if (serviceId == null) {
            return ServiceCoverageResult.builder()
                    .serviceId(null)
                    .serviceName(serviceName)
                    .covered(false)
                    .reason("No service ID provided")
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

        Optional<BenefitPolicyRule> ruleOpt = ruleRepository.findBestRuleForService(
                policy.getId(), serviceId, categoryId, null);

        if (ruleOpt.isEmpty()) {
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

        BenefitPolicyRule rule = ruleOpt.get();

        return ServiceCoverageResult.builder()
                .serviceId(serviceId)
                .serviceName(service.getName())
                .serviceCode(service.getCode())
                .categoryId(categoryId)
                .categoryName(null)
                .covered(true)
                .coveragePercent(rule.getEffectiveCoveragePercent())
                .amountLimit(rule.getAmountLimit())
                .timesLimit(rule.getTimesLimit())
                .requiresPreApproval(rule.isRequiresPreApproval())
                .ruleId(rule.getId())
                .ruleType(rule.isCategoryRule() ? "CATEGORY" : "SERVICE")
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
     */
    public void validateAmountLimits(
            Member member,
            BenefitPolicy benefitPolicy,
            BigDecimal requestedAmount,
            LocalDate serviceDate) {

        if (benefitPolicy == null) {
            throw new BusinessRuleException("Member has no BenefitPolicy assigned");
        }

        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No amount to validate
        }

        log.debug("🔍 Validating amount limits for member {} amount {} on {}",
                member.getId(), requestedAmount, serviceDate);

        // Check annual limit from BenefitPolicy
        BigDecimal annualLimit = benefitPolicy.getAnnualLimit();
        if (annualLimit != null && annualLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usedAmount = calculateUsedAmountForYear(member.getId(), serviceDate.getYear());
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
            BigDecimal totalUsed = calculateTotalUsedForMember(member.getId());
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
            BigDecimal familyUsed = calculateFamilyUsedAmountForYear(principalId, serviceDate.getYear());
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
     * Uses service code to lookup the medical service.
     */
    private void validateWaitingPeriodForClaimLine(
            BenefitPolicy benefitPolicy,
            ClaimLine line,
            LocalDate memberStartDate,
            LocalDate serviceDate,
            long daysSinceEnrollment) {

        String serviceCode = line.getServiceCode();
        if (serviceCode == null || serviceCode.isBlank()) {
            return; // No service code to check
        }

        // Try to find the medical service by code
        Optional<MedicalService> serviceOpt = serviceRepository.findByCode(serviceCode);
        if (serviceOpt.isEmpty()) {
            log.debug("Service code {} not found, skipping waiting period check for this line", serviceCode);
            return;
        }

        MedicalService service = serviceOpt.get();
        Long categoryId = resolveCategoryIdForCoverage(service);

        Optional<BenefitPolicyRule> ruleOpt = ruleRepository.findBestRuleForService(
                benefitPolicy.getId(), service.getId(), categoryId, null);

        if (ruleOpt.isPresent()) {
            BenefitPolicyRule rule = ruleOpt.get();
            Integer ruleWaitingDays = rule.getWaitingPeriodDays();

            if (ruleWaitingDays != null && ruleWaitingDays > 0 && daysSinceEnrollment < ruleWaitingDays) {
                LocalDate eligibleDate = memberStartDate.plusDays(ruleWaitingDays);
                String serviceName = service.getName() != null ? service.getName() : service.getName();
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

        Optional<BenefitPolicyRule> ruleOpt = ruleRepository.findBestRuleForService(
                benefitPolicy.getId(), serviceId, categoryId, null);

        if (ruleOpt.isEmpty()) {
            String serviceName = service.getName() != null ? service.getName() : service.getName();
            throw new BusinessRuleException(
                    String.format("الخدمة '%s' غير مغطاة في وثيقة المزايا '%s'",
                            serviceName, benefitPolicy.getName()));
        }

        // Check if the rule is active
        BenefitPolicyRule rule = ruleOpt.get();
        if (!rule.isActive()) {
            String serviceName = service.getName() != null ? service.getName() : service.getName();
            throw new BusinessRuleException(
                    String.format("قاعدة التغطية للخدمة '%s' غير نشطة",
                            serviceName));
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
     * Calculate used amount for a member in a specific year using DB aggregation.
     * PERFORMANCE: O(1) query replaces loading all claims into memory.
     */
    private BigDecimal calculateUsedAmountForYear(Long memberId, int year) {
        List<com.waad.tba.modules.claim.entity.ClaimStatus> validStatuses = List.of(
                com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED,
                com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED,
                com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED);
        return claimRepository.sumApprovedAmountByMemberAndYear(memberId, year, validStatuses);
    }

    /**
     * Calculate used amount for a whole family in a specific year using DB
     * aggregation.
     */
    private BigDecimal calculateFamilyUsedAmountForYear(Long principalId, int year) {
        List<com.waad.tba.modules.claim.entity.ClaimStatus> validStatuses = List.of(
                com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED,
                com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED,
                com.waad.tba.modules.claim.entity.ClaimStatus.BATCHED);
        return claimRepository.sumApprovedAmountByFamilyAndYear(principalId, year, validStatuses);
    }

    /**
     * Calculate total used for a member (all time).
     */
    private BigDecimal calculateTotalUsedForMember(Long memberId) {
        // We could optimize this too, but usually annual is the bottleneck
        List<Claim> claims = claimRepository.findByMemberId(memberId);

        return claims.stream()
                .filter(c -> c.getApprovedAmount() != null)
                .filter(c -> c.getStatus() != com.waad.tba.modules.claim.entity.ClaimStatus.REJECTED)
                .map(Claim::getApprovedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get remaining coverage for a member (for UI display).
     */
    public BigDecimal getRemainingCoverage(Member member, LocalDate asOfDate) {
        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy == null) {
            return null;
        }

        BigDecimal annualLimit = policy.getAnnualLimit();
        if (annualLimit == null || annualLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // Unlimited or not configured
        }

        BigDecimal used = calculateUsedAmountForYear(member.getId(), asOfDate.getYear());
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
     * 3. Else → return POLICY_DEFAULT
     * 
     * @param policyId   The benefit policy ID
     * @param serviceId  The medical service ID
     * @param categoryId The medical category ID (from service)
     * @return Resolved coverage rule or null if not found
     */
    public ResolvedCoverage resolveCoverage(Long policyId, Long serviceId, Long categoryId) {
        log.debug("🔍 Resolving coverage: policyId={}, serviceId={}, categoryId={}",
                policyId, serviceId, categoryId);

        // Step 1: Try to find SERVICE_RULE
        Optional<BenefitPolicyRule> serviceRuleOpt = ruleRepository
                .findByBenefitPolicyIdAndMedicalServiceIdAndActiveTrue(policyId, serviceId);

        if (serviceRuleOpt.isPresent()) {
            BenefitPolicyRule rule = serviceRuleOpt.get();
            log.debug("✅ Found SERVICE_RULE: ruleId={}", rule.getId());
            return ResolvedCoverage.fromRule(rule, CoverageSource.SERVICE_RULE);
        }

        // Step 2: Try to find CATEGORY_RULE
        if (categoryId != null) {
            Optional<BenefitPolicyRule> categoryRuleOpt = ruleRepository
                    .findActiveCategoryRule(policyId, categoryId);

            if (categoryRuleOpt.isPresent()) {
                BenefitPolicyRule rule = categoryRuleOpt.get();
                log.debug("✅ Found CATEGORY_RULE: ruleId={}", rule.getId());
                return ResolvedCoverage.fromRule(rule, CoverageSource.CATEGORY_RULE);
            }
        }

        // Step 3: Return POLICY_DEFAULT
        BenefitPolicy policy = policyRepository.findById(policyId).orElse(null);
        if (policy != null) {
            log.debug("⚠️ No specific rule found, using POLICY_DEFAULT");
            return ResolvedCoverage.builder()
                    .covered(true)
                    .coveragePercent(policy.getDefaultCoveragePercent() != null
                            ? policy.getDefaultCoveragePercent()
                            : SYSTEM_DEFAULT_COVERAGE_PERCENT)
                    .requiresPreApproval(DEFAULT_REQUIRES_PA)
                    .source(CoverageSource.POLICY_DEFAULT)
                    .build();
        }

        log.warn("❌ No coverage found for policyId={}, serviceId={}", policyId, serviceId);
        return null;
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
        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy == null) {
            return DEFAULT_REQUIRES_PA;
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return DEFAULT_REQUIRES_PA;
        }

        ResolvedCoverage coverage = resolveCoverage(policy.getId(), serviceId, resolveCategoryIdForCoverage(service));
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
        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy == null) {
            return 0;
        }

        MedicalService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) {
            return 0;
        }

        ResolvedCoverage coverage = resolveCoverage(policy.getId(), serviceId, resolveCategoryIdForCoverage(service));
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

        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy == null || serviceIds == null || serviceIds.isEmpty()) {
            // Return all zeros
            for (Long id : serviceIds != null ? serviceIds : java.util.Collections.<Long>emptyList()) {
                result.put(id, 0);
            }
            return result;
        }

        Long policyId = policy.getId();
        int policyDefault = policy.getDefaultCoveragePercent() != null
                ? policy.getDefaultCoveragePercent()
                : SYSTEM_DEFAULT_COVERAGE_PERCENT;

        // Step 1: Load all services with their categories in a single query
        java.util.List<MedicalService> services = serviceRepository.findAllById(serviceIds);
        java.util.Map<Long, Long> serviceToCategory = new java.util.HashMap<>();
        java.util.Set<Long> categoryIds = new java.util.HashSet<>();

        for (MedicalService svc : services) {
            Long resolvedCategoryId = resolveCategoryIdForCoverage(svc);
            serviceToCategory.put(svc.getId(), resolvedCategoryId);
            if (resolvedCategoryId != null) {
                categoryIds.add(resolvedCategoryId);
            }
        }

        // Step 2: Preload ALL service rules for this policy in a single query
        java.util.List<BenefitPolicyRule> serviceRules = ruleRepository.findByBenefitPolicyIdAndActiveTrue(policyId);
        java.util.Map<Long, BenefitPolicyRule> serviceRuleMap = new java.util.HashMap<>();
        java.util.Map<Long, BenefitPolicyRule> categoryRuleMap = new java.util.HashMap<>();

        for (BenefitPolicyRule rule : serviceRules) {
            if (rule.getMedicalService() != null) {
                // SERVICE_RULE
                serviceRuleMap.put(rule.getMedicalService().getId(), rule);
            } else if (rule.getMedicalCategory() != null) {
                // CATEGORY_RULE
                categoryRuleMap.put(rule.getMedicalCategory().getId(), rule);
            }
        }

        // Step 3: Resolve coverage for each service
        for (Long serviceId : serviceIds) {
            int coveragePercent = 0;

            // Priority 1: SERVICE_RULE
            BenefitPolicyRule serviceRule = serviceRuleMap.get(serviceId);
            if (serviceRule != null) {
                coveragePercent = serviceRule.getEffectiveCoveragePercent();
                log.debug("📋 Batch coverage for service {}: {}% (SERVICE_RULE)", serviceId, coveragePercent);
            } else {
                // Priority 2: CATEGORY_RULE
                Long categoryId = serviceToCategory.get(serviceId);
                if (categoryId != null) {
                    BenefitPolicyRule categoryRule = categoryRuleMap.get(categoryId);
                    if (categoryRule != null) {
                        coveragePercent = categoryRule.getEffectiveCoveragePercent();
                        log.debug("📋 Batch coverage for service {}: {}% (CATEGORY_RULE)", serviceId, coveragePercent);
                    } else {
                        // Priority 3: POLICY_DEFAULT
                        coveragePercent = policyDefault;
                        log.debug("📋 Batch coverage for service {}: {}% (POLICY_DEFAULT)", serviceId, coveragePercent);
                    }
                } else {
                    // No category, use policy default
                    coveragePercent = policyDefault;
                    log.debug("📋 Batch coverage for service {}: {}% (POLICY_DEFAULT, no category)", serviceId,
                            coveragePercent);
                }
            }

            result.put(serviceId, coveragePercent);
        }

        log.info("📊 Batch coverage resolved for {} services in policy {}", serviceIds.size(), policyId);
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
        private Integer timesLimit;
        private boolean requiresPreApproval;
        private Integer waitingPeriodDays;
        private Long ruleId;
        private CoverageSource source;

        public static ResolvedCoverage fromRule(BenefitPolicyRule rule, CoverageSource source) {
            return ResolvedCoverage.builder()
                    .covered(true)
                    .coveragePercent(rule.getEffectiveCoveragePercent())
                    .amountLimit(rule.getAmountLimit())
                    .timesLimit(rule.getTimesLimit())
                    .requiresPreApproval(rule.isRequiresPreApproval())
                    .waitingPeriodDays(rule.getWaitingPeriodDays())
                    .ruleId(rule.getId())
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
        POLICY_DEFAULT // Policy-level default coverage
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
        if (service == null || service.getId() == null) {
            return null;
        }

        return serviceCategoryRepository
                .findFirstByServiceIdAndActiveTrueOrderByIsPrimaryDescIdAsc(service.getId())
                .map(link -> link.getCategoryId())
                .orElse(service.getCategoryId());
    }
}
