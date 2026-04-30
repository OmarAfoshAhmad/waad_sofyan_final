package com.waad.tba.modules.claim.service;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.provider.service.ProviderNetworkService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CostCalculationService (FINANCIAL ENGINE 2026)
 * 
 * Responsible for calculating claim financials:
 * - Patient Co-Pay (Weighted Average)
 * - Annual Deductible Application
 * - Insurance Share
 * - Out-of-Pocket Maximums
 * 
 * FINANCIAL REFORM (2026-04-29):
 * All calculations are now based on the NET APPROVED amount (Requested - Refused)
 * instead of the raw Requested amount. This ensures patients are not charged
 * co-pay on services that were rejected or refused.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostCalculationService {

    private final ProviderNetworkService providerNetworkService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;

    /**
     * Calculate cost breakdown for a claim.
     * 
     * Adjudication Logic:
     * 1. Calculate Total Refused (Rejected lines + Refused amounts)
     * 2. Determine Net Base Amount = Total Requested - Total Refused
     * 3. Calculate Weighted Co-Pay % from non-rejected lines
     * 4. Apply Deductible to Net Base
     * 5. Apply Co-Pay % to remaining Net Base
     * 6. Validate Out-of-Pocket limits
     * 
     * @param claim The claim to calculate costs for
     * @return CostBreakdown with all calculated amounts
     */
    @Transactional(readOnly = true)
    public CostBreakdown calculateCosts(Claim claim) {
        BigDecimal requestedAmount = claim.getRequestedAmount();

        if (requestedAmount == null) {
            throw new com.waad.tba.common.exception.BusinessRuleException(
                    "FINANCIAL_ERROR: Cannot calculate costs - requested amount is null.");
        }

        if (requestedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return CostBreakdown.zero();
        }

        Member member = claim.getMember();
        BenefitPolicy benefitPolicy = member != null ? member.getBenefitPolicy() : null;
        NetworkType networkType = providerNetworkService.determineNetworkTypeByName(claim.getProviderName());

        // ═══════════════════════════════════════════════════════════════════════════════
        // STEP 0: Calculate Net Base (Exclude Refused/Rejected amounts)
        // ═══════════════════════════════════════════════════════════════════════════════
        List<ClaimLine> lines = claim.getLines() != null ? claim.getLines() : List.of();
        
        BigDecimal totalRefused = lines.stream()
                .map(l -> {
                    BigDecimal lineRequested = (l.getRequestedUnitPrice() != null ? l.getRequestedUnitPrice() : l.getUnitPrice())
                            .multiply(BigDecimal.valueOf(l.getQuantity()));
                    if (Boolean.TRUE.equals(l.getRejected())) return lineRequested;
                    return l.getRefusedAmount() != null ? l.getRefusedAmount() : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netBaseAmount = requestedAmount.subtract(totalRefused).max(BigDecimal.ZERO);

        // STEP 1: Weighted Co-Pay calculation based on NET amounts
        BigDecimal coPayPercent = calculateWeightedCopayFromLines(claim, member, networkType);
        
        // STEP 2: Deductible application
        BigDecimal annualDeductible = getAnnualDeductible(benefitPolicy);
        BigDecimal deductibleMet = getDeductibleMetThisPeriod(member, claim);
        BigDecimal remainingDeductible = annualDeductible.subtract(deductibleMet).max(BigDecimal.ZERO);

        BigDecimal deductibleApplied = netBaseAmount.min(remainingDeductible);
        BigDecimal afterDeductible = netBaseAmount.subtract(deductibleApplied);

        // STEP 3: Co-Pay application
        BigDecimal coPayAmount = afterDeductible.multiply(coPayPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal insuranceAmount = afterDeductible.subtract(coPayAmount);

        // STEP 4: Out-of-Pocket Limit
        BigDecimal outOfPocketMax = getOutOfPocketMax(benefitPolicy);
        BigDecimal outOfPocketSpent = getOutOfPocketSpentThisPeriod(member, claim);
        BigDecimal remainingOutOfPocket = outOfPocketMax.subtract(outOfPocketSpent).max(BigDecimal.ZERO);

        BigDecimal totalPatientResponsibility = deductibleApplied.add(coPayAmount);

        if (remainingOutOfPocket.compareTo(BigDecimal.ZERO) > 0 && totalPatientResponsibility.compareTo(remainingOutOfPocket) > 0) {
            BigDecimal excess = totalPatientResponsibility.subtract(remainingOutOfPocket);
            totalPatientResponsibility = remainingOutOfPocket;
            insuranceAmount = insuranceAmount.add(excess);
        }

        // Final balance check
        BigDecimal totalCalculated = totalPatientResponsibility.add(insuranceAmount);
        if (totalCalculated.compareTo(netBaseAmount) != 0) {
            insuranceAmount = netBaseAmount.subtract(totalPatientResponsibility);
        }

        return new CostBreakdown(
                requestedAmount,
                totalRefused,
                annualDeductible,
                deductibleMet,
                deductibleApplied,
                coPayPercent,
                coPayAmount,
                insuranceAmount,
                totalPatientResponsibility,
                outOfPocketMax,
                outOfPocketSpent.add(totalPatientResponsibility),
                networkType
        );
    }

    /**
     * Calculate weighted co-pay percent based on line-level coverage.
     * Uses NET amounts (TotalPrice - RefusedAmount) for weighting.
     */
    private BigDecimal calculateWeightedCopayFromLines(Claim claim, Member member, NetworkType networkType) {
        List<ClaimLine> lines = claim.getLines();
        BenefitPolicy benefitPolicy = member != null ? member.getBenefitPolicy() : null;

        if (lines == null || lines.isEmpty() || member == null || benefitPolicy == null) {
            return getCoPayPercent(benefitPolicy, networkType);
        }

        List<Long> categoryIds = lines.stream()
                .map(l -> l.getAppliedCategoryId() != null ? l.getAppliedCategoryId() : l.getServiceCategoryId())
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        java.util.Map<Long, Integer> coverageMap = benefitPolicyCoverageService.batchGetCoveragePercentsByCategory(member, categoryIds);

        BigDecimal totalNetAmount = BigDecimal.ZERO;
        BigDecimal weightedCopaySum = BigDecimal.ZERO;

        for (ClaimLine line : lines) {
            if (Boolean.TRUE.equals(line.getRejected())) continue;

            BigDecimal rPrice = line.getRequestedUnitPrice() != null ? line.getRequestedUnitPrice() : line.getUnitPrice();
            BigDecimal totalPrice = rPrice != null ? rPrice.multiply(BigDecimal.valueOf(line.getQuantity())) : BigDecimal.ZERO;
            BigDecimal refused = line.getRefusedAmount() != null ? line.getRefusedAmount() : BigDecimal.ZERO;
            BigDecimal netAmount = totalPrice.subtract(refused).max(BigDecimal.ZERO);

            if (netAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            Long categoryId = line.getAppliedCategoryId() != null ? line.getAppliedCategoryId() : line.getServiceCategoryId();
            int coveragePercent = 80; // default
            if (categoryId != null) {
                coveragePercent = coverageMap.getOrDefault(categoryId, benefitPolicy.getDefaultCoveragePercent() != null ? benefitPolicy.getDefaultCoveragePercent() : 80);
            }

            int copayPercent = 100 - Math.min(100, Math.max(0, coveragePercent));
            weightedCopaySum = weightedCopaySum.add(netAmount.multiply(new BigDecimal(copayPercent)));
            totalNetAmount = totalNetAmount.add(netAmount);
        }

        if (totalNetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return getCoPayPercent(benefitPolicy, networkType);
        }

        return weightedCopaySum.divide(totalNetAmount, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal getCoPayPercent(BenefitPolicy policy, NetworkType networkType) {
        if (policy == null) return new BigDecimal("20.00");
        // Use policy-level defaults if no rules exist (100 - coverage%)
        BigDecimal defaultCopay = BigDecimal.valueOf(100 - policy.getDefaultCoveragePercent());
        
        if (networkType == NetworkType.OUT_OF_NETWORK) {
            return defaultCopay; // Standardize on default if specific OON copay not in entity
        }
        return defaultCopay;
    }

    private BigDecimal getAnnualDeductible(BenefitPolicy policy) {
        return (policy != null && policy.getAnnualDeductible() != null) ? policy.getAnnualDeductible() : BigDecimal.ZERO;
    }

    private BigDecimal getDeductibleMetThisPeriod(Member member, Claim claim) {
        return BigDecimal.ZERO; // Simplified for MVP
    }

    private BigDecimal getOutOfPocketMax(BenefitPolicy policy) {
        return (policy != null && policy.getOutOfPocketMax() != null) ? policy.getOutOfPocketMax() : new BigDecimal("5000.00");
    }

    private BigDecimal getOutOfPocketSpentThisPeriod(Member member, Claim claim) {
        return BigDecimal.ZERO; // Simplified for MVP
    }

    public record CostBreakdown(
            BigDecimal requestedAmount,
            BigDecimal refusedAmount,
            BigDecimal annualDeductible,
            BigDecimal deductibleMetYTD,
            BigDecimal deductibleApplied,
            BigDecimal coPayPercent,
            BigDecimal coPayAmount,
            BigDecimal insuranceAmount,
            BigDecimal patientResponsibility,
            BigDecimal outOfPocketMax,
            BigDecimal outOfPocketYTD,
            NetworkType networkType
    ) {
        public static CostBreakdown zero() {
            return new CostBreakdown(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NetworkType.IN_NETWORK
            );
        }

        public boolean isDeductibleMet() {
            return deductibleMetYTD.compareTo(annualDeductible) >= 0;
        }

        public boolean isOutOfPocketMaxReached() {
            return outOfPocketYTD.compareTo(outOfPocketMax) >= 0;
        }

        public String getSummary() {
            return String.format(
                "Req: %.2f, Ref: %.2f, Ded: %.2f, CoPay: %.2f%% (%.2f), Ins: %.2f, Pat: %.2f",
                requestedAmount, refusedAmount, deductibleApplied, coPayPercent, coPayAmount, insuranceAmount, patientResponsibility
            );
        }
    }
}
