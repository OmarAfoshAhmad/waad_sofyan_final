package com.waad.tba.modules.eligibility.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.eligibility.domain.*;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Eligibility Engine Service Implementation
 * Phase E1 - Eligibility Engine
 * 
 * Core implementation of the eligibility check engine.
 * 
 * Features:
 * - Pluggable rule architecture via Spring injection
 * - Sequential rule evaluation with priority ordering
 * - Hard failure stops evaluation, soft failures continue
 * - Full audit logging of every check
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EligibilityEngineServiceImpl implements EligibilityEngineService {

    // Repositories
    private final MemberRepository memberRepository;
    private final ProviderRepository providerRepository;
    private final EligibilityAuditRecorder auditRecorder;
    private final BenefitPolicyCoverageService coverageService;

    // Security
    private final AuthorizationService authorizationService;

    // Rules - auto-injected by Spring, sorted by @Order annotation
    private final List<EligibilityRule> rules;

    @Override
    @Transactional(readOnly = true)
    public EligibilityResult checkEligibility(EligibilityCheckRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        log.info("[Eligibility] Starting check - RequestID: {}, MemberID: {}, ServiceDate: {}",
                requestId, request.getMemberId(), request.getServiceDate());

        try {
            // Build context
            EligibilityContext context = buildContext(request, requestId);

            // Evaluate rules
            EligibilityResult result = evaluateRules(context, startTime);

            // Log to audit -- own transaction, can never affect the decision above
            boolean audited = auditRecorder.record(context, result);
            if (!audited) {
                result = result.toBuilder().auditRecorded(false).build();
            }

            log.info("[Eligibility] Check complete - RequestID: {}, Eligible: {}, Status: {}, Time: {}ms",
                    requestId, result.isEligible(), result.getStatus(), result.getProcessingTimeMs());

            return result;

        } catch (Exception e) {
            log.error("[Eligibility] Error during check - RequestID: {}, Error: {}", requestId, e.getMessage(), e);

            // Return system error result
            return EligibilityResult.notEligible(
                    requestId,
                    null,
                    List.of(EligibilityResult.ReasonDetail.from(
                            EligibilityReason.SYSTEM_ERROR,
                            e.getMessage())),
                    System.currentTimeMillis() - startTime,
                    0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EligibilityResult checkEligibility(EligibilityContext context) {
        long startTime = System.currentTimeMillis();

        log.info("[Eligibility] Internal check - RequestID: {}, MemberID: {}",
                context.getRequestId(), context.getMemberId());

        EligibilityResult result = evaluateRules(context, startTime);

        // Log to audit -- own transaction, can never affect the decision above
        boolean audited = auditRecorder.record(context, result);
        if (!audited) {
            result = result.toBuilder().auditRecorded(false).build();
        }

        return result;
    }

    @Override
    public List<String> getActiveRules() {
        return rules.stream()
                .sorted(Comparator.comparingInt(EligibilityRule::getPriority))
                .map(EligibilityRule::getRuleCode)
                .toList();
    }

    // ============================================
    // Private Methods
    // ============================================

    /**
     * Build the eligibility context from request and resolved entities
     */
    private EligibilityContext buildContext(EligibilityCheckRequest request, String requestId) {
        // Resolve member
        Member member = null;
        if (request.getMemberId() != null) {
            member = memberRepository.findById(request.getMemberId()).orElse(null);
        }

        // Resolve BenefitPolicy from member (CANONICAL - only policy model)
        BenefitPolicy benefitPolicy = null;
        Long benefitPolicyId = null;
        if (member != null && member.getBenefitPolicy() != null) {
            benefitPolicy = member.getBenefitPolicy();
            benefitPolicyId = benefitPolicy.getId();
        }

        // Resolve provider (optional)
        Provider provider = null;
        if (request.getProviderId() != null) {
            provider = providerRepository.findById(request.getProviderId()).orElse(null);
        }

        // Get current user for security context
        User currentUser = authorizationService.getCurrentUser();
        Long userId = currentUser != null ? currentUser.getId() : null;
        String username = currentUser != null ? currentUser.getUsername() : null;
        Long employerId = currentUser != null ? currentUser.getEmployerId() : null;
        boolean isSuperAdmin = currentUser != null && authorizationService.isSuperAdmin(currentUser);

        // Get request info
        HttpServletRequest httpRequest = getHttpServletRequest();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest != null ? httpRequest.getHeader("User-Agent") : null;

        return EligibilityContext.builder()
                .requestId(requestId)
                .memberId(request.getMemberId())
                .benefitPolicyId(benefitPolicyId)
                .providerId(request.getProviderId())
                .serviceDate(request.getServiceDate())
                .serviceCode(request.getServiceCode())
                .medicalCategoryId(request.getMedicalCategoryId())
                .medicalServiceId(request.getMedicalServiceId())
                .encounterType(request.getEncounterType() != null
                        ? request.getEncounterType()
                        : com.waad.tba.modules.providercontract.enums.EncounterType.OUTPATIENT)
                .member(member)
                .benefitPolicy(benefitPolicy)
                .provider(provider)
                .employer(member != null ? member.getEmployer() : null)
                .checkedByUserId(userId)
                .checkedByUsername(username)
                .companyScopeId(employerId) // Using employerId as company scope in simplified model
                .superAdmin(isSuperAdmin)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .checkTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Evaluate all applicable rules
     */
    private EligibilityResult evaluateRules(EligibilityContext context, long startTime) {
        List<EligibilityResult.ReasonDetail> failures = new ArrayList<>();
        List<EligibilityResult.ReasonDetail> warnings = new ArrayList<>();
        int rulesEvaluated = 0;

        // Sort rules by priority
        List<EligibilityRule> sortedRules = rules.stream()
                .sorted(Comparator.comparingInt(EligibilityRule::getPriority))
                .toList();

        for (EligibilityRule rule : sortedRules) {
            // Check if rule is applicable
            if (!rule.isApplicable(context)) {
                log.debug("[Eligibility] Rule {} skipped - not applicable", rule.getRuleCode());
                continue;
            }

            rulesEvaluated++;
            log.debug("[Eligibility] Evaluating rule: {} (priority: {})",
                    rule.getRuleCode(), rule.getPriority());

            try {
                RuleResult result = rule.evaluate(context);

                if (!result.isPassed()) {
                    EligibilityResult.ReasonDetail detail = EligibilityResult.ReasonDetail.from(result);

                    if (rule.isHardRule() && result.getReason() != null && result.getReason().isHardFailure()) {
                        // Hard failure - stop evaluation
                        failures.add(detail);
                        log.info("[Eligibility] Hard failure at rule {}: {}",
                                rule.getRuleCode(), result.getReasonCode());

                        return buildResult(context, false, failures, warnings,
                                startTime, rulesEvaluated);
                    } else {
                        // Soft failure - add warning and continue
                        warnings.add(detail);
                        log.info("[Eligibility] Warning at rule {}: {}",
                                rule.getRuleCode(), result.getReasonCode());
                    }
                } else {
                    log.debug("[Eligibility] Rule {} passed", rule.getRuleCode());
                }

            } catch (Exception e) {
                log.error("[Eligibility] Rule {} threw exception: {}", rule.getRuleCode(), e.getMessage());
                // Treat rule exception as hard failure
                failures.add(EligibilityResult.ReasonDetail.from(
                        EligibilityReason.SYSTEM_ERROR,
                        "Rule " + rule.getRuleCode() + " error: " + e.getMessage()));
                return buildResult(context, false, failures, warnings, startTime, rulesEvaluated);
            }
        }

        // All rules passed
        return buildResult(context, true, failures, warnings, startTime, rulesEvaluated);
    }

    /**
     * Build the final result
     */
    private EligibilityResult buildResult(EligibilityContext context, boolean eligible,
            List<EligibilityResult.ReasonDetail> failures,
            List<EligibilityResult.ReasonDetail> warnings,
            long startTime, int rulesEvaluated) {
        long processingTime = System.currentTimeMillis() - startTime;
        EligibilityResult.EligibilitySnapshot snapshot = buildSnapshot(context);

        if (!eligible) {
            List<EligibilityResult.ReasonDetail> allReasons = new ArrayList<>(failures);
            allReasons.addAll(warnings);
            return EligibilityResult.notEligible(
                    context.getRequestId(),
                    snapshot,
                    allReasons,
                    processingTime,
                    rulesEvaluated);
        }

        if (!warnings.isEmpty()) {
            return EligibilityResult.eligibleWithWarnings(
                    context.getRequestId(),
                    snapshot,
                    warnings,
                    processingTime,
                    rulesEvaluated);
        }

        return EligibilityResult.eligible(
                context.getRequestId(),
                snapshot,
                processingTime,
                rulesEvaluated);
    }

    /**
     * Build the snapshot from context
     */
    private EligibilityResult.EligibilitySnapshot buildSnapshot(EligibilityContext context) {
        Member member = context.getMember();
        BenefitPolicy benefitPolicy = context.getBenefitPolicy();
        Provider provider = context.getProvider();

        EligibilityResult.EligibilitySnapshot.EligibilitySnapshotBuilder builder = EligibilityResult.EligibilitySnapshot
                .builder()
                // Member
                .memberId(member != null ? member.getId() : context.getMemberId())
                .memberName(member != null ? member.getFullName() : null)
                .memberCivilId(member != null ? member.getNationalNumber()
                        : context.getMemberId() != null ? context.getMemberId().toString() : null)
                .memberStatus(member != null && member.getStatus() != null ? member.getStatus().name() : null)
                .memberCardNumber(member != null ? member.getCardNumber() : null)
                // Policy (BenefitPolicy)
                .policyId(benefitPolicy != null ? benefitPolicy.getId() : context.getBenefitPolicyId())
                .policyNumber(benefitPolicy != null ? benefitPolicy.getPolicyCode() : null)
                .policyStatus(
                        benefitPolicy != null && benefitPolicy.getStatus() != null ? benefitPolicy.getStatus().name()
                                : null)
                .coverageStart(benefitPolicy != null ? benefitPolicy.getStartDate() : null)
                .coverageEnd(benefitPolicy != null ? benefitPolicy.getEndDate() : null)
                .productName(benefitPolicy != null ? benefitPolicy.getName() : null)
                // Employer
                .employerId(context.getMemberEmployerId())
                .employerName(context.getEmployer() != null ? context.getEmployer().getName() : null)
                // Provider
                .providerId(provider != null ? provider.getId() : context.getProviderId())
                .providerName(provider != null ? provider.getName() : null)
                // Service
                .serviceDate(context.getServiceDate())
                .serviceCode(context.getServiceCode())
                .medicalCategoryId(context.getMedicalCategoryId())
                .medicalServiceId(context.getMedicalServiceId());

        // Resolve detailed coverage if medical service or category is provided
        if (benefitPolicy != null
                && (context.getMedicalServiceId() != null || context.getMedicalCategoryId() != null)) {
            BenefitPolicyCoverageService.ResolvedCoverage coverage = coverageService.resolveCoverage(
                    benefitPolicy.getId(),
                    context.getMedicalServiceId(),
                    context.getMedicalCategoryId(),
                    null, // overrideCategoryId
                    member != null ? member.getId() : null, // memberId
                    context.getServiceDate(),
                    null, // claimIdToExclude
                    com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext.valueOf(
                            context.getEncounterType().name()),
                    1.0,
                    null,
                    true
            );

            if (coverage != null) {
                builder.coveragePercent(coverage.getCoveragePercent())
                        .patientCopayPercent(100 - coverage.getCoveragePercent())
                        .requiresPreApproval(coverage.isRequiresPreApproval())
                        .matchingCategoryId(coverage.getMatchingCategoryId());

                if (coverage.getAmountLimit() != null) {
                    builder.benefitLimit(coverage.getAmountLimit().doubleValue());
                }
                if (coverage.getUsedAmount() != null) {
                    builder.usedAmount(coverage.getUsedAmount().doubleValue());
                }
                if (coverage.getRemainingAmount() != null) {
                    builder.remainingAmount(coverage.getRemainingAmount().doubleValue());
                }
            }
        }

        return builder.build();
    }

    /**
     * Get HttpServletRequest from context
     */
    private HttpServletRequest getHttpServletRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null)
            return null;

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
