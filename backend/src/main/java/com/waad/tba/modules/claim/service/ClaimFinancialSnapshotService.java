package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;

import lombok.RequiredArgsConstructor;

/**
 * Canonical finalizer for claim money. It is the only service allowed to turn
 * calculated line coverage into the persisted claim-level financial snapshot.
 */
@Service
@RequiredArgsConstructor
public class ClaimFinancialSnapshotService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AtomicFinancialService atomicFinancialService;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;

    @Transactional
    public BigDecimal finalizeSnapshot(Claim claim) {
        CostCalculationService.CostBreakdown breakdown =
                atomicFinancialService.calculateCostsWithAtomicDeductible(claim);

        BigDecimal requested = value(claim.getRequestedAmount());
        BigDecimal refused = value(claim.getRefusedAmount());
        BigDecimal accepted = requested.subtract(refused).max(BigDecimal.ZERO);
        BigDecimal patient = value(breakdown.patientResponsibility()).min(accepted);
        BigDecimal companyBeforeDiscount = accepted.subtract(patient);
        BigDecimal discount = calculateDiscount(companyBeforeDiscount, claim.getAppliedDiscountPercent());
        BigDecimal payable = companyBeforeDiscount.subtract(discount).max(BigDecimal.ZERO);

        if (claim.getMember() != null && claim.getMember().getBenefitPolicy() != null) {
            benefitPolicyCoverageService.validateAmountLimits(
                    claim.getMember(), claim.getMember().getBenefitPolicy(), payable, claim.getServiceDate());
        }

        claim.setApprovedAmount(scale(payable));
        claim.setNetProviderAmount(scale(payable));
        claim.setPatientCoPay(scale(patient));
        claim.setRefusedAmount(scale(refused));
        claim.setCompanyDiscountAmount(scale(discount));
        claim.setDifferenceAmount(scale(requested.subtract(payable)));
        claim.setDeductibleApplied(scale(value(breakdown.deductibleApplied()).min(patient)));
        claim.setCoPayPercent(accepted.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : patient.multiply(HUNDRED).divide(accepted, 2, RoundingMode.HALF_UP));
        claim.markCoverageSynced();
        return payable;
    }

    private BigDecimal calculateDiscount(BigDecimal amount, BigDecimal percent) {
        BigDecimal rate = value(percent);
        return rate.signum() <= 0 ? BigDecimal.ZERO.setScale(2)
                : amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP).min(amount);
    }

    private static BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static BigDecimal scale(BigDecimal amount) {
        return value(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
