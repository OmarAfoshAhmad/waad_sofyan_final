package com.waad.tba.modules.claim.service;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.provider.service.ProviderNetworkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostCalculationServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ProviderNetworkService providerNetworkService;

    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;

    @InjectMocks
    private CostCalculationService costCalculationService;

    private Member testMember;
    private BenefitPolicy testPolicy;
    private Claim testClaim;

    @BeforeEach
    void setUp() {
        testPolicy = BenefitPolicy.builder()
                .id(1L)
                .annualDeductible(new BigDecimal("100.00"))
                .defaultCoveragePercent(80) // 20% copay
                .outOfPocketMax(new BigDecimal("1000.00"))
                .build();

        testMember = Member.builder()
                .id(1L)
                .benefitPolicy(testPolicy)
                .build();

        testClaim = Claim.builder()
                .id(1L)
                .member(testMember)
                .requestedAmount(new BigDecimal("500.00"))
                .providerName("Hospital A")
                .lines(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Should calculate basic costs with no deductible met")
    void calculateCosts_NoDeductibleMet() {
        // Arrange
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);
        when(claimRepository.sumDeductibleForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(BigDecimal.ZERO);
        when(claimRepository.sumPatientCopayForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(BigDecimal.ZERO);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Requested: 500
        // Deductible: 100 (applied)
        // Remainder: 400
        // Copay: 400 * 20% = 80
        // Insurance: 400 - 80 = 320
        // Total Patient: 100 + 80 = 180
        assertEquals(new BigDecimal("500.00"), result.requestedAmount());
        assertEquals(new BigDecimal("100.00"), result.deductibleApplied());
        assertEquals(new BigDecimal("80.00"), result.coPayAmount());
        assertEquals(new BigDecimal("320.00"), result.insuranceAmount());
        assertEquals(new BigDecimal("180.00"), result.patientResponsibility());
    }

    @Test
    @DisplayName("Should calculate costs when deductible is already met")
    void calculateCosts_DeductibleAlreadyMet() {
        // Arrange
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);
        when(claimRepository.sumDeductibleForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(new BigDecimal("100.00"));
        when(claimRepository.sumPatientCopayForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(new BigDecimal("200.00"));

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Requested: 500
        // Deductible Applied: 0
        // Copay: 500 * 20% = 100
        // Insurance: 400
        // Total Patient: 100
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deductibleApplied()));
        assertEquals(new BigDecimal("100.00"), result.coPayAmount());
        assertEquals(new BigDecimal("400.00"), result.insuranceAmount());
    }

    @Test
    @DisplayName("Should apply out-of-network penalty (+20%)")
    void calculateCosts_OutOfNetwork() {
        // Arrange
        testPolicy.setDefaultCoveragePercent(100); // 0% copay in-network
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.OUT_OF_NETWORK);
        when(claimRepository.sumDeductibleForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(new BigDecimal("100.00"));
        when(claimRepository.sumPatientCopayForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(BigDecimal.ZERO);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Copay should be 20% (0% base + 20% penalty)
        // 500 * 20% = 100
        assertEquals(new BigDecimal("20.00"), result.coPayPercent());
        assertEquals(new BigDecimal("100.00"), result.coPayAmount());
        assertEquals(new BigDecimal("400.00"), result.insuranceAmount());
    }

    @Test
    @DisplayName("Should respect out-of-pocket maximum")
    void calculateCosts_OutOfPocketMax() {
        // Arrange
        testPolicy.setOutOfPocketMax(new BigDecimal("200.00"));
        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);
        when(claimRepository.sumDeductibleForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(new BigDecimal("100.00"));
        // Already spent 150 OOP this year. Cap is 200. Remaining is 50.
        when(claimRepository.sumPatientCopayForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(new BigDecimal("150.00"));

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Calculation would normally be: Copay 100 (500 * 20%).
        // Patient only has 50 left to reaching cap.
        // Total Patient Responsibility should be 50.
        // Insurance covers the rest: 500 - 50 = 450.
        assertEquals(new BigDecimal("50.00"), result.patientResponsibility());
        assertEquals(new BigDecimal("450.00"), result.insuranceAmount());
        assertTrue(result.isOutOfPocketMaxReached());
    }

    @Test
    @DisplayName("Should throw exception for negative requested amount")
    void calculateCosts_NegativeAmount() {
        // Arrange
        testClaim.setRequestedAmount(new BigDecimal("-100.00"));

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> costCalculationService.calculateCosts(testClaim));
    }

    @Test
    @DisplayName("Should calculate weighted co-pay from lines")
    void calculateWeightedCopay_MultipleLines() {
        // Arrange
        ClaimLine line1 = ClaimLine.builder()
                .serviceCategoryId(101L)
                .unitPrice(new BigDecimal("100.00"))
                .quantity(1)
                .requestedUnitPrice(new BigDecimal("100.00"))
                .build();

        ClaimLine line2 = ClaimLine.builder()
                .serviceCategoryId(102L)
                .unitPrice(new BigDecimal("200.00"))
                .quantity(1)
                .requestedUnitPrice(new BigDecimal("200.00"))
                .build();

        testClaim.setLines(List.of(line1, line2));
        testClaim.setRequestedAmount(new BigDecimal("300.00"));

        when(providerNetworkService.determineNetworkTypeByName(anyString())).thenReturn(NetworkType.IN_NETWORK);
        when(claimRepository.sumDeductibleForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(new BigDecimal("100.00"));
        when(claimRepository.sumPatientCopayForYear(anyLong(), anyInt(), anyList(), anyLong()))
                .thenReturn(BigDecimal.ZERO);

        Map<Long, Integer> coverageMap = new HashMap<>();
        coverageMap.put(101L, 90); // 10% copay
        coverageMap.put(102L, 70); // 30% copay
        when(benefitPolicyCoverageService.batchGetCoveragePercentsByCategory(any(), anyList())).thenReturn(coverageMap);

        // Act
        CostCalculationService.CostBreakdown result = costCalculationService.calculateCosts(testClaim);

        // Assert
        // Weighted CoPay = (100*10 + 200*30) / 300 = 7000 / 300 = 23.33
        assertEquals(new BigDecimal("23.33"), result.coPayPercent());
        // coPayAmount = 300 * 23.33 / 100 = 69.99
        assertEquals(new BigDecimal("69.99"), result.coPayAmount());
    }
}
