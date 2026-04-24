package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverageEngineServiceTest {

    @Mock
    private BenefitPolicyRuleService benefitPolicyRuleService;

    @InjectMocks
    private CoverageEngineService coverageEngineService;

    @Test
    @DisplayName("manualRefusedAmount must not be overwritten when byCompany is zero")
    void manualRefused_should_not_be_overwritten_when_company_is_zero() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(10L)
                        .effectiveCoveragePercent(0)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("covered", true, "hasLimit", false));

        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("L-1")
                .serviceId(200L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .contractPrice(BigDecimal.ZERO)
                .manualRefusedAmount(new BigDecimal("20.00"))
                .manualRefusalReason("Manual adjustment")
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(1L)
                .memberId(100L)
                .serviceYear(2026)
                .lines(List.of(line))
                .build();

        CoverageResult result = coverageEngineService.calculateBulk(request).get(0);

        assertEquals(new BigDecimal("20.00"), result.getManualRefusedAmount());
        assertEquals(new BigDecimal("20.00"), result.getFinalRefusedAmount());
        assertEquals(new BigDecimal("20.00"), result.getRefusedAmount());
        assertEquals(new BigDecimal("0.00"), result.getCompanyShare());
    }

    @Test
    @DisplayName("must throw when systemRefused + manualRefused exceeds requested total")
    void should_throw_when_total_refused_exceeds_claim_amount() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(22L)
                        .effectiveCoveragePercent(100)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));

        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new HashMap<>() {
                    {
                        put("covered", true);
                        put("hasLimit", true);
                        put("ruleId", 22L);
                        put("timesLimit", null);
                        put("amountLimit", BigDecimal.ZERO);
                        put("usedCount", 0);
                        put("usedAmount", BigDecimal.ZERO);
                        put("exceeded", true);
                        put("timesExceeded", false);
                        put("amountExceeded", true);
                    }
                });

        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("L-2")
                .serviceId(201L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .contractPrice(BigDecimal.ZERO)
                .manualRefusedAmount(new BigDecimal("50.00"))
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(1L)
                .memberId(101L)
                .serviceYear(2026)
                .lines(List.of(line))
                .build();

        assertThrows(IllegalArgumentException.class, () -> coverageEngineService.calculateBulk(request));
    }

    @Test
    @DisplayName("normal case should return final refused as system + manual")
    void should_compute_final_refused_as_system_plus_manual() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(30L)
                        .effectiveCoveragePercent(80)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));
        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("covered", true, "hasLimit", false));

        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("L-3")
                .serviceId(300L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .contractPrice(new BigDecimal("90.00"))
                .manualRefusedAmount(new BigDecimal("20.00"))
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(2L)
                .memberId(102L)
                .serviceYear(2026)
                .lines(List.of(line))
                .build();

        CoverageResult result = coverageEngineService.calculateBulk(request).get(0);

        assertEquals(new BigDecimal("10.00"), result.getSystemRefusedAmount());
        assertEquals(new BigDecimal("20.00"), result.getManualRefusedAmount());
        assertEquals(new BigDecimal("30.00"), result.getFinalRefusedAmount());
        assertEquals(new BigDecimal("30.00"), result.getRefusedAmount());
    }
}
