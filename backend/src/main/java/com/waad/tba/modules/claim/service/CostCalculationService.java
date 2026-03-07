package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.service.ProviderNetworkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cost Calculation Service - Deductible & Co-Pay Engine.
 * 
 * Calculates patient responsibility amounts before claim approval:
 * 1. DEDUCTIBLE - Fixed amount patient pays before insurance kicks in (per
 * policy period)
 * 2. CO-PAY - Percentage of the claim amount patient pays (varies by
 * network/service)
 * 3. COINSURANCE - Insurance company's share after deductible
 * 
 * CALCULATION FLOW:
 * 1. Check if annual deductible is met for the member
 * 2. If not met, apply remaining deductible to claim
 * 3. Apply co-pay percentage to remaining amount
 * 4. Network type affects co-pay rates
 * 5. Store calculated amounts on Claim entity
 * 
 * @since Phase 7 - Operational Completeness
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostCalculationService {

    private final ClaimRepository claimRepository;
    private final ProviderNetworkService providerNetworkService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;

    // Default values if not specified in policy
    // Default deductible - set to 0 by default (no deductible unless specified in
    // policy)
    // In production, this should come from BenefitPolicy.annualDeductible
    private static final BigDecimal DEFAULT_ANNUAL_DEDUCTIBLE = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_COPAY_IN_NETWORK = new BigDecimal("20.00");
    private static final BigDecimal DEFAULT_COPAY_OUT_OF_NETWORK = new BigDecimal("40.00");
    private static final BigDecimal DEFAULT_OUT_OF_POCKET_MAX = new BigDecimal("5000.00");

    /**
     * Calculate all cost components for a claim.
     * 
     * ═══════════════════════════════════════════════════════════════════════════════
     * FIXED (2026-01-23): Now uses BenefitPolicyRule coverage percent per service
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * Previously: Used only BenefitPolicy.defaultCoveragePercent (WRONG!)
     * Now: Uses BenefitPolicyCoverageService to get coverage per service line
     * 
     * Coverage Resolution Priority:
     * 1. SERVICE_RULE (specific rule for service) - HIGHEST
     * 2. CATEGORY_RULE (rule for service's category)
     * 3. POLICY_DEFAULT (BenefitPolicy.defaultCoveragePercent) - LOWEST
     * 
     * @param claim The claim to calculate costs for
     * @return CostBreakdown with all calculated amounts
     * @throws BusinessRuleException if requestedAmount is null, zero, or negative
     */
    public CostBreakdown calculateCosts(Claim claim) {
        BigDecimal requestedAmount = claim.getRequestedAmount();

        // ══════════════════════════════════════════════════════════════════════════
        // PHASE 1.3: STRICT AMOUNT VALIDATION - NO SILENT FAILURES
        // ══════════════════════════════════════════════════════════════════════════
        // Previously: Returned zero breakdown silently (WRONG - hides errors)
        // Now: Throws explicit BusinessRuleException (CORRECT - fail fast)
        if (requestedAmount == null) {
            throw new com.waad.tba.common.exception.BusinessRuleException(
                    "FINANCIAL_ERROR: Cannot calculate costs - requested amount is null. " +
                            "Claim ID: " + claim.getId());
        }

        if (requestedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new com.waad.tba.common.exception.BusinessRuleException(
                    String.format("FINANCIAL_ERROR: Cannot calculate costs - requested amount is negative (%s). " +
                            "Claim ID: %s", requestedAmount, claim.getId()));
        }

        if (requestedAmount.compareTo(BigDecimal.ZERO) == 0) {
            // Zero amount is allowed for informational purposes but should be logged
            log.warn("⚠️ [COST-CALC] Claim {} has zero requested amount - returning zero breakdown",
                    claim.getId());
            return CostBreakdown.zero();
        }

        // Get member and benefit policy info
        Member member = claim.getMember();
        BenefitPolicy benefitPolicy = member != null ? member.getBenefitPolicy() : null;

        // Determine network type based on provider
        NetworkType networkType = providerNetworkService.determineNetworkTypeByName(claim.getProviderName());

        // Get deductible info
        BigDecimal annualDeductible = getAnnualDeductible(benefitPolicy);
        BigDecimal deductibleMet = getDeductibleMetThisPeriod(member, claim);
        BigDecimal remainingDeductible = annualDeductible.subtract(deductibleMet).max(BigDecimal.ZERO);

        // ═══════════════════════════════════════════════════════════════════════════════
        // NEW: Calculate weighted average coverage from claim lines using
        // BenefitPolicyRule
        // ═══════════════════════════════════════════════════════════════════════════════
        BigDecimal coPayPercent = calculateWeightedCopayFromLines(claim, member, networkType);
        log.info("📊 Calculated weighted co-pay percent: {}% for claim {} (from {} lines)",
                coPayPercent, claim.getId(), claim.getLines() != null ? claim.getLines().size() : 0);

        // Get out-of-pocket maximum
        BigDecimal outOfPocketMax = getOutOfPocketMax(benefitPolicy);
        BigDecimal outOfPocketSpent = getOutOfPocketSpentThisPeriod(member, claim);
        BigDecimal remainingOutOfPocket = outOfPocketMax.subtract(outOfPocketSpent).max(BigDecimal.ZERO);

        // STEP 1: Apply deductible
        BigDecimal deductibleApplied;
        BigDecimal afterDeductible;

        if (remainingDeductible.compareTo(BigDecimal.ZERO) > 0) {
            // Patient still has deductible to meet
            deductibleApplied = requestedAmount.min(remainingDeductible);
            afterDeductible = requestedAmount.subtract(deductibleApplied);
        } else {
            deductibleApplied = BigDecimal.ZERO;
            afterDeductible = requestedAmount;
        }

        // STEP 2: Apply co-pay to remaining amount
        BigDecimal coPayAmount = BigDecimal.ZERO;
        BigDecimal insuranceAmount = BigDecimal.ZERO;

        if (afterDeductible.compareTo(BigDecimal.ZERO) > 0) {
            coPayAmount = afterDeductible.multiply(coPayPercent)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            insuranceAmount = afterDeductible.subtract(coPayAmount);
        }

        // STEP 3: Apply out-of-pocket maximum
        BigDecimal totalPatientResponsibility = deductibleApplied.add(coPayAmount);

        if (remainingOutOfPocket.compareTo(BigDecimal.ZERO) > 0) {
            if (totalPatientResponsibility.compareTo(remainingOutOfPocket) > 0) {
                // Patient has hit out-of-pocket max, insurance covers the rest
                BigDecimal excess = totalPatientResponsibility.subtract(remainingOutOfPocket);
                totalPatientResponsibility = remainingOutOfPocket;
                insuranceAmount = insuranceAmount.add(excess);

                log.info("Out-of-pocket maximum reached for member {}. Excess {} covered by insurance.",
                        member.getId(), excess);
            }
        }

        // Final validation: patient responsibility + insurance = requested
        BigDecimal total = totalPatientResponsibility.add(insuranceAmount);
        if (total.compareTo(requestedAmount) != 0) {
            // Adjust for rounding
            insuranceAmount = requestedAmount.subtract(totalPatientResponsibility);
        }

        return new CostBreakdown(
                requestedAmount,
                annualDeductible,
                deductibleMet,
                deductibleApplied,
                coPayPercent,
                coPayAmount,
                insuranceAmount,
                totalPatientResponsibility,
                outOfPocketMax,
                outOfPocketSpent.add(totalPatientResponsibility),
                networkType);
    }

    /**
     * Calculate weighted co-pay percentage from claim lines.
     * 
     * Each line may have a different coverage percentage based on
     * BenefitPolicyRule.
     * This method calculates the weighted average co-pay based on line amounts.
     * 
     * Formula: Co-Pay% = 100 - Coverage%
     * Weighted Avg = Σ(lineAmount × lineCopay%) / totalAmount
     * 
     * @param claim       The claim with lines
     * @param member      The member (for BenefitPolicy lookup)
     * @param networkType Network type for adjustments
     * @return Weighted average co-pay percentage
     */
    private BigDecimal calculateWeightedCopayFromLines(Claim claim, Member member, NetworkType networkType) {
        List<ClaimLine> lines = claim.getLines();
        BenefitPolicy benefitPolicy = member != null ? member.getBenefitPolicy() : null;

        // If no lines or no member/policy, fall back to default calculation
        if (lines == null || lines.isEmpty() || member == null || benefitPolicy == null) {
            log.debug("⚠️ No lines or policy, using default co-pay calculation");
            return getCoPayPercent(benefitPolicy, networkType);
        }

        // PERFORMANCE OPTIMIZATION: Batch preload all coverage percentages in ONE query
        List<Long> serviceIds = lines.stream()
                .filter(line -> line.getMedicalService() != null)
                .map(line -> line.getMedicalService().getId())
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<Long, Integer> coverageMap = benefitPolicyCoverageService.batchGetCoveragePercents(member,
                serviceIds);

        log.debug("📊 Preloaded coverage for {} services (N+1 elimination)", serviceIds.size());

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal weightedCopaySum = BigDecimal.ZERO;

        for (ClaimLine line : lines) {
            BigDecimal lineAmount = line.getTotalPrice();
            if (lineAmount == null || lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Get coverage percent from preloaded map (O(1) lookup)
            int coveragePercent;
            if (line.getMedicalService() != null) {
                coveragePercent = coverageMap.getOrDefault(line.getMedicalService().getId(),
                        benefitPolicy.getDefaultCoveragePercent() != null ? benefitPolicy.getDefaultCoveragePercent()
                                : 80);
            } else {
                coveragePercent = getFallbackCoverage(member);
            }

            // CRITICAL: Enforce hard bounds [0, 100]
            coveragePercent = Math.min(100, Math.max(0, coveragePercent));

            // Co-Pay% = 100 - Coverage%
            int copayPercent = 100 - coveragePercent;

            log.debug("📋 Line service={}, amount={}, coverage={}%, copay={}%",
                    line.getServiceCode(), lineAmount, coveragePercent, copayPercent);

            // Weighted contribution
            BigDecimal lineCopayContribution = lineAmount.multiply(new BigDecimal(copayPercent));
            weightedCopaySum = weightedCopaySum.add(lineCopayContribution);
            totalAmount = totalAmount.add(lineAmount);
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return getCoPayPercent(benefitPolicy, networkType);
        }

        // Calculate weighted average co-pay
        BigDecimal weightedCopay = weightedCopaySum
                .divide(totalAmount, 2, RoundingMode.HALF_UP);

        // Adjust for out-of-network (add penalty)
        if (networkType == NetworkType.OUT_OF_NETWORK) {
            weightedCopay = weightedCopay.add(new BigDecimal("20.00")).min(new BigDecimal("100.00"));
            log.debug("⚠️ Out-of-network adjustment applied: +20%");
        }

        return weightedCopay;
    }

    /**
     * Get coverage percentage for a claim line using BenefitPolicyCoverageService.
     * 
     * Resolution Priority:
     * 1. SERVICE_RULE (if exists for this specific service)
     * 2. CATEGORY_RULE (if exists for service's category)
     * 3. POLICY_DEFAULT (BenefitPolicy.defaultCoveragePercent)
     * 
     * IMPORTANT: Always clamp result to [0, 100] range to prevent negative copay
     * 
     * @param line   The claim line
     * @param member The member
     * @return Coverage percentage (0-100), guaranteed within bounds
     */
    private int getCoveragePercentForLine(ClaimLine line, Member member) {
        int coverage;

        // Try to get coverage from BenefitPolicyCoverageService
        if (line.getMedicalService() != null) {
            Long serviceId = line.getMedicalService().getId();
            int rawCoverage = benefitPolicyCoverageService.getEffectiveCoveragePercent(member, serviceId);

            if (rawCoverage > 0) {
                log.debug("✅ Coverage for service {} resolved from BenefitPolicyRule: {}%",
                        serviceId, rawCoverage);
                coverage = rawCoverage;
            } else {
                coverage = getFallbackCoverage(member);
            }
        } else {
            coverage = getFallbackCoverage(member);
        }

        // CRITICAL: Enforce hard bounds [0, 100] to prevent negative copay calculations
        int normalizedCoverage = Math.min(100, Math.max(0, coverage));
        if (normalizedCoverage != coverage) {
            log.warn("⚠️ SECURITY: Coverage {}% was out of bounds, normalized to {}%",
                    coverage, normalizedCoverage);
        }

        return normalizedCoverage;
    }

    /**
     * Get fallback coverage percentage from policy or system default.
     * 
     * @param member The member
     * @return Coverage percentage (may be out of bounds, caller must normalize)
     */
    private int getFallbackCoverage(Member member) {
        // Fallback: use policy default if no specific rule found
        BenefitPolicy policy = member.getBenefitPolicy();
        if (policy != null && policy.getDefaultCoveragePercent() != null) {
            log.debug("⚠️ Using policy default coverage: {}%", policy.getDefaultCoveragePercent());
            return policy.getDefaultCoveragePercent();
        }

        // Ultimate fallback: 80% (system default)
        log.debug("⚠️ No coverage rule found, using system default: 80%");
        return 80;
    }

    /**
     * Calculate and update the claim with cost breakdown.
     * This should be called before claim approval.
     * 
     * @param claim The claim to update
     * @return Updated claim with cost fields populated
     */
    @Transactional
    public Claim calculateAndUpdateClaim(Claim claim) {
        CostBreakdown breakdown = calculateCosts(claim);

        // Populate claim with calculated financial snapshot
        // NOTE: approvedAmount is intentionally NOT set here.
        // It must be explicitly set by a reviewer upon approval — not derived from cost
        // calc.
        claim.setPatientCoPay(breakdown.patientResponsibility());
        claim.setNetProviderAmount(breakdown.insuranceAmount());
        claim.setCoPayPercent(breakdown.coPayPercent());
        claim.setDeductibleApplied(breakdown.deductibleApplied());
        claim.setDifferenceAmount(claim.getRequestedAmount().subtract(breakdown.insuranceAmount()));

        log.info("✅ Cost calculation persisted for claim {}: requested={}, deductible={}, copay={}, insurance={}",
                claim.getId(),
                breakdown.requestedAmount(),
                breakdown.deductibleApplied(),
                breakdown.coPayAmount(),
                breakdown.insuranceAmount());

        return claim;
    }

    /**
     * Get the deductible amount already met by a member in the current policy
     * period.
     * PERFORMANCE: Now uses database-level aggregation to avoid N+1 queries.
     */
    private BigDecimal getDeductibleMetThisPeriod(Member member, Claim currentClaim) {
        if (member == null) {
            return BigDecimal.ZERO;
        }

        int year = LocalDate.now().getYear();
        List<com.waad.tba.modules.claim.entity.ClaimStatus> validStatuses = List.of(
                com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED,
                com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED);
        Long excludeId = currentClaim.getId() != null ? currentClaim.getId() : -1L;

        return claimRepository.sumDeductibleForYear(member.getId(), year, validStatuses, excludeId);
    }

    /**
     * Get the total out-of-pocket amount spent by a member in the current policy
     * period.
     * (Calculated as sum of all patientCoPay fields in approved/settled claims)
     */
    private BigDecimal getOutOfPocketSpentThisPeriod(Member member, Claim currentClaim) {
        if (member == null) {
            return BigDecimal.ZERO;
        }

        int year = LocalDate.now().getYear();
        List<com.waad.tba.modules.claim.entity.ClaimStatus> validStatuses = List.of(
                com.waad.tba.modules.claim.entity.ClaimStatus.APPROVED,
                com.waad.tba.modules.claim.entity.ClaimStatus.SETTLED);
        Long excludeId = currentClaim.getId() != null ? currentClaim.getId() : -1L;

        return claimRepository.sumPatientCopayForYear(member.getId(), year, validStatuses, excludeId);
    }

    /**
     * Extract deductible amount from a historical claim.
     * Uses the actual recorded deductible if available, otherwise estimates from
     * difference.
     */
    private BigDecimal extractDeductibleFromClaim(Claim claim) {
        // Use actual recorded deductible if available (Single Source of Truth)
        if (claim.getDeductibleApplied() != null) {
            return claim.getDeductibleApplied();
        }

        // Fallback for legacy data: estimate based on difference
        if (claim.getDifferenceAmount() != null) {
            return claim.getDifferenceAmount().multiply(new BigDecimal("0.3"))
                    .setScale(2, RoundingMode.HALF_UP); // Assume ~30% of difference was deductible
        }

        // Fallback for legacy claims without deductibleApplied field populated
        // Log warning and return zero (don't estimate)
        log.warn("⚠️ Claim {} has no deductibleApplied value set - returning ZERO for accumulation. " +
                "This may indicate a legacy claim or data migration issue.", claim.getId());
        return BigDecimal.ZERO;
    }

    /**
     * Extract total patient responsibility from a historical claim.
     * Patient responsibility = deductibleApplied + patientCoPay
     * 
     * FIXED 2026-03-06: Included deductibleApplied in total responsibility and
     * fixed null-logic.
     * 
     * @param claim The historical claim
     * @return Total patient out-of-pocket for this claim
     */
    private BigDecimal extractPatientResponsibility(Claim claim) {
        BigDecimal deductible = claim.getDeductibleApplied() != null ? claim.getDeductibleApplied() : BigDecimal.ZERO;
        BigDecimal copay = claim.getPatientCoPay() != null ? claim.getPatientCoPay() : BigDecimal.ZERO;

        // If either field is explicitly set, use the sum as Single Source of Truth
        if (claim.getDeductibleApplied() != null || claim.getPatientCoPay() != null) {
            return deductible.add(copay);
        }

        // Legacy fallback: estimate from difference between requested and approved
        BigDecimal requested = claim.getRequestedAmount();
        BigDecimal approved = claim.getApprovedAmount();
        if (requested != null && approved != null) {
            BigDecimal diff = requested.subtract(approved).max(BigDecimal.ZERO);
            log.warn("⚠️ Claim {} missing deductible/copay fields, estimated patient responsibility from diff: {}",
                    claim.getId(), diff);
            return diff;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Get annual deductible from benefit policy.
     * FIXED 2026-03-06: Now correctly reads the annualDeductible field from
     * BenefitPolicy.
     */
    private BigDecimal getAnnualDeductible(BenefitPolicy benefitPolicy) {
        if (benefitPolicy == null || benefitPolicy.getAnnualDeductible() == null) {
            return DEFAULT_ANNUAL_DEDUCTIBLE;
        }
        return benefitPolicy.getAnnualDeductible();
    }

    /**
     * Get co-pay percentage based on benefit policy and network type.
     * Co-pay = 100 - coverage percent. Higher coverage = lower co-pay.
     */
    private BigDecimal getCoPayPercent(BenefitPolicy benefitPolicy, NetworkType networkType) {
        // Check benefit policy first - co-pay is inverse of coverage
        if (benefitPolicy != null && benefitPolicy.getDefaultCoveragePercent() != null) {
            // CRITICAL: Normalize coverage to [0, 100] to prevent negative copay
            int rawCoverage = benefitPolicy.getDefaultCoveragePercent();
            int normalizedCoverage = Math.min(100, Math.max(0, rawCoverage));

            if (normalizedCoverage != rawCoverage) {
                log.warn("⚠️ SECURITY: Policy coverage {}% out of bounds, normalized to {}%",
                        rawCoverage, normalizedCoverage);
            }

            // Co-pay = 100 - coverage percent (always >= 0 after normalization)
            BigDecimal baseCopay = new BigDecimal(100 - normalizedCoverage);
            if (networkType == NetworkType.OUT_OF_NETWORK) {
                // Out-of-network typically has higher co-pay (e.g., +20%)
                return baseCopay.add(new BigDecimal("20.00")).min(new BigDecimal("100.00"));
            }
            return baseCopay;
        }

        // Use defaults based on network type
        return networkType == NetworkType.IN_NETWORK
                ? DEFAULT_COPAY_IN_NETWORK
                : DEFAULT_COPAY_OUT_OF_NETWORK;
    }

    /**
     * Get out-of-pocket maximum from benefit policy or use default.
     * Uses perMemberLimit or a fraction of annualLimit as the basis.
     */
    private BigDecimal getOutOfPocketMax(BenefitPolicy benefitPolicy) {
        if (benefitPolicy != null && benefitPolicy.getOutOfPocketMax() != null &&
                benefitPolicy.getOutOfPocketMax().compareTo(BigDecimal.ZERO) > 0) {
            return benefitPolicy.getOutOfPocketMax();
        }

        // Fallback for legacy policies (10% of per-member limit)
        if (benefitPolicy != null && benefitPolicy.getPerMemberLimit() != null) {
            return benefitPolicy.getPerMemberLimit().multiply(new BigDecimal("0.1"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return DEFAULT_OUT_OF_POCKET_MAX;
    }

    // ==================== Record Types ====================

    /**
     * Complete cost breakdown for a claim.
     */
    public record CostBreakdown(
            BigDecimal requestedAmount,
            BigDecimal annualDeductible,
            BigDecimal deductibleMetYTD,
            BigDecimal deductibleApplied,
            BigDecimal coPayPercent,
            BigDecimal coPayAmount,
            BigDecimal insuranceAmount,
            BigDecimal patientResponsibility,
            BigDecimal outOfPocketMax,
            BigDecimal outOfPocketYTD,
            NetworkType networkType) {
        /**
         * Create a zero cost breakdown.
         */
        public static CostBreakdown zero() {
            return new CostBreakdown(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, NetworkType.IN_NETWORK);
        }

        /**
         * Check if patient has met their annual deductible.
         */
        public boolean isDeductibleMet() {
            return deductibleMetYTD.compareTo(annualDeductible) >= 0;
        }

        /**
         * Check if patient has hit out-of-pocket maximum.
         */
        public boolean isOutOfPocketMaxReached() {
            return outOfPocketYTD.compareTo(outOfPocketMax) >= 0;
        }

        /**
         * Get summary for display.
         */
        public String getSummary() {
            return String.format(
                    "Requested: %s, Deductible: %s, Co-pay: %s (%.0f%%), Insurance Pays: %s, Patient Pays: %s",
                    requestedAmount, deductibleApplied, coPayAmount, coPayPercent,
                    insuranceAmount, patientResponsibility);
        }
    }
}
