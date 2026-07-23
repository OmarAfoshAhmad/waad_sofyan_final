package com.waad.tba.modules.claim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;

@ExtendWith(MockitoExtension.class)
class ClaimFinancialSnapshotServiceTest {
    @Mock
    private AtomicFinancialService atomicFinancialService;
    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @InjectMocks
    private ClaimFinancialSnapshotService service;

    @Test
    void finalizerProducesBalancedSnapshotWithCopayRefusalAndContractDiscount() {
        Claim claim = Claim.builder()
                .requestedAmount(new BigDecimal("1000.00"))
                .refusedAmount(new BigDecimal("100.00"))
                .appliedDiscountPercent(new BigDecimal("10.00"))
                .build();
        CostCalculationService.CostBreakdown breakdown = new CostCalculationService.CostBreakdown(
                new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("20.00"),
                new BigDecimal("130.00"), new BigDecimal("720.00"), new BigDecimal("180.00"),
                new BigDecimal("5000.00"), new BigDecimal("180.00"), NetworkType.IN_NETWORK);
        when(atomicFinancialService.calculateCostsWithAtomicDeductible(claim)).thenReturn(breakdown);

        BigDecimal payable = service.finalizeSnapshot(claim);

        assertEquals(new BigDecimal("648.00"), payable);
        assertEquals(new BigDecimal("648.00"), claim.getApprovedAmount());
        assertEquals(new BigDecimal("648.00"), claim.getNetProviderAmount());
        assertEquals(new BigDecimal("180.00"), claim.getPatientCoPay());
        assertEquals(new BigDecimal("100.00"), claim.getRefusedAmount());
        assertEquals(new BigDecimal("72.00"), claim.getCompanyDiscountAmount());
        assertEquals(new BigDecimal("50.00"), claim.getDeductibleApplied());
        assertEquals(new BigDecimal("20.00"), claim.getCoPayPercent());
        assertEquals(new BigDecimal("352.00"), claim.getDifferenceAmount());
        assertEquals(new BigDecimal("1000.00"), claim.getPatientCoPay()
                .add(claim.getRefusedAmount())
                .add(claim.getCompanyDiscountAmount())
                .add(claim.getNetProviderAmount()));
    }
}
