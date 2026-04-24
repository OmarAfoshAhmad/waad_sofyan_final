package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyRuleResponseDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
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

    @Mock
    private MedicalAuditLogService medicalAuditLogService;

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

    @Test
    @DisplayName("timesLimit must hard-stop and reject entire service without amount-limit processing")
    void should_hard_stop_when_times_limit_exceeded() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(44L)
                        .effectiveCoveragePercent(80)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));

        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new HashMap<>() {
                    {
                        put("covered", true);
                        put("hasLimit", true);
                        put("ruleId", 44L);
                        put("timesLimit", 5);
                        put("amountLimit", new BigDecimal("1000.00"));
                        put("usedCount", 5);
                        put("usedAmount", new BigDecimal("100.00"));
                    }
                });

        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("L-4")
                .serviceId(400L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(3L)
                .memberId(103L)
                .serviceYear(2026)
                .lines(List.of(line))
                .build();

        CoverageResult result = coverageEngineService.calculateBulk(request).get(0);

        assertEquals(new BigDecimal("100.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("0.00"), result.getApprovedTotal());
        assertEquals("USAGE_TIMES_LIMIT_EXCEEDED", result.getRefusalReason());
        assertEquals(true, result.getUsageDetails().isTimesExceeded());
        assertEquals(false, result.getUsageDetails().isAmountExceeded());
    }

    @Test
    @DisplayName("amountLimit should partially cap approved total")
    void should_partially_cap_when_amount_limit_exceeded() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(55L)
                        .effectiveCoveragePercent(80)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));

        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new HashMap<>() {
                    {
                        put("covered", true);
                        put("hasLimit", true);
                        put("ruleId", 55L);
                        put("timesLimit", null);
                        put("amountLimit", new BigDecimal("1000.00"));
                        put("usedCount", 1);
                        put("usedAmount", new BigDecimal("900.00"));
                    }
                });

        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("L-5")
                .serviceId(500L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("200.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(4L)
                .memberId(104L)
                .serviceYear(2026)
                .lines(List.of(line))
                .build();

        CoverageResult result = coverageEngineService.calculateBulk(request).get(0);

        assertEquals(new BigDecimal("100.00"), result.getLimitRefused());
        assertEquals(new BigDecimal("100.00"), result.getApprovedTotal());
        assertEquals("USAGE_AMOUNT_LIMIT_EXCEEDED", result.getRefusalReason());
    }

    @Test
    @DisplayName("must not increment usage counters when line is fully rejected by amount limit")
    void should_prevent_double_deduction_when_amount_limit_already_exhausted() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(66L)
                        .effectiveCoveragePercent(80)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));

        Map<String, Object> exhaustedUsage = new HashMap<>();
        exhaustedUsage.put("covered", true);
        exhaustedUsage.put("hasLimit", true);
        exhaustedUsage.put("ruleId", 66L);
        exhaustedUsage.put("timesLimit", 1);
        exhaustedUsage.put("amountLimit", new BigDecimal("50.00"));
        exhaustedUsage.put("usedCount", 0);
        exhaustedUsage.put("usedAmount", new BigDecimal("50.00"));

        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(exhaustedUsage)
                .thenReturn(exhaustedUsage);

        ClaimLineInput line1 = ClaimLineInput.builder()
                .lineId("L-6-1")
                .serviceId(600L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        ClaimLineInput line2 = ClaimLineInput.builder()
                .lineId("L-6-2")
                .serviceId(600L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("100.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(5L)
                .memberId(105L)
                .serviceYear(2026)
                .lines(List.of(line1, line2))
                .build();

        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        assertEquals("USAGE_AMOUNT_LIMIT_EXCEEDED", results.get(0).getRefusalReason());
        assertEquals(false, results.get(0).getUsageDetails().isTimesExceeded());

        assertEquals("USAGE_AMOUNT_LIMIT_EXCEEDED", results.get(1).getRefusalReason());
        assertEquals(false, results.get(1).getUsageDetails().isTimesExceeded());
    }

    @Test
    @DisplayName("bulk lines must share remaining amount cap and never exceed total amountLimit")
    void should_cap_total_approved_across_bulk_lines_by_remaining_limit() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(77L)
                        .effectiveCoveragePercent(100)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));

        Map<String, Object> usage = new HashMap<>();
        usage.put("covered", true);
        usage.put("hasLimit", true);
        usage.put("ruleId", 77L);
        usage.put("timesLimit", null);
        usage.put("amountLimit", new BigDecimal("100.00"));
        usage.put("usedCount", 0);
        usage.put("usedAmount", BigDecimal.ZERO);

        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage)
                .thenReturn(usage);

        ClaimLineInput line1 = ClaimLineInput.builder()
                .lineId("L-7-1")
                .serviceId(700L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("70.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        ClaimLineInput line2 = ClaimLineInput.builder()
                .lineId("L-7-2")
                .serviceId(700L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("70.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(6L)
                .memberId(106L)
                .serviceYear(2026)
                .lines(List.of(line1, line2))
                .build();

        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        assertEquals(new BigDecimal("70.00"), results.get(0).getApprovedTotal());
        assertEquals(new BigDecimal("0.00"), results.get(0).getLimitRefused());

        assertEquals(new BigDecimal("30.00"), results.get(1).getApprovedTotal());
        assertEquals(new BigDecimal("40.00"), results.get(1).getLimitRefused());
        assertEquals("USAGE_AMOUNT_LIMIT_EXCEEDED", results.get(1).getRefusalReason());

        BigDecimal totalApproved = results.stream()
                .map(CoverageResult::getApprovedTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), totalApproved);
    }

    @Test
    @DisplayName("amount cap must be applied before company/patient split when coverage percent is below 100")
    void should_apply_amount_cap_before_financial_split_for_partial_coverage() {
        when(benefitPolicyRuleService.findCoverageForService(any(), any(), any(), any()))
                .thenReturn(Optional.of(BenefitPolicyRuleResponseDto.builder()
                        .id(88L)
                        .effectiveCoveragePercent(80)
                        .requiresPreApproval(false)
                        .medicalCategoryId(51L)
                        .build()));

        Map<String, Object> usage = new HashMap<>();
        usage.put("covered", true);
        usage.put("hasLimit", true);
        usage.put("ruleId", 88L);
        usage.put("timesLimit", null);
        usage.put("amountLimit", new BigDecimal("100.00"));
        usage.put("usedCount", 0);
        usage.put("usedAmount", BigDecimal.ZERO);

        when(benefitPolicyRuleService.checkUsageLimit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usage)
                .thenReturn(usage);

        ClaimLineInput line1 = ClaimLineInput.builder()
                .lineId("L-8-1")
                .serviceId(800L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("70.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        ClaimLineInput line2 = ClaimLineInput.builder()
                .lineId("L-8-2")
                .serviceId(800L)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("70.00"))
                .contractPrice(BigDecimal.ZERO)
                .build();

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(7L)
                .memberId(107L)
                .serviceYear(2026)
                .lines(List.of(line1, line2))
                .build();

        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        assertEquals(new BigDecimal("70.00"), results.get(0).getApprovedTotal());
        assertEquals(new BigDecimal("56.00"), results.get(0).getCompanyShare());
        assertEquals(new BigDecimal("14.00"), results.get(0).getPatientShare());

        assertEquals(new BigDecimal("30.00"), results.get(1).getApprovedTotal());
        assertEquals(new BigDecimal("40.00"), results.get(1).getLimitRefused());
        assertEquals(new BigDecimal("24.00"), results.get(1).getCompanyShare());
        assertEquals(new BigDecimal("6.00"), results.get(1).getPatientShare());
        assertEquals("USAGE_AMOUNT_LIMIT_EXCEEDED", results.get(1).getRefusalReason());

        BigDecimal totalApproved = results.stream()
                .map(CoverageResult::getApprovedTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), totalApproved);
    }
}
