package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.providercontract.enums.EncounterType;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_JALIANA_LIVE_TEST", matches = "true")
class JalianaLiveCoverageIntegrationTest {

    @Autowired CoverageEngineService coverageEngineService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void dentalImplant_shouldApplyButNotExposeGeneralCeilingAsBenefitLimit() {
        Long policyId = requiredLong("select id from benefit_policies where policy_code='POL-2026-001'");
        Long memberId = requiredLong("select min(id) from members where employer_id=1 and benefit_policy_id=" + policyId + " and active=true");
        Long pricingItemId = requiredLong("select id from provider_contract_pricing_items where service_code='JAL-TST-032' and active=true");

        CoverageResult outpatient = calculate(policyId, memberId, pricingItemId, EncounterType.OUTPATIENT);
        assertThat(outpatient.isNotCovered()).isFalse();
        assertThat(outpatient.getCoveragePercent()).isEqualTo(50);
        assertThat(outpatient.getCompanyShare()).isEqualByComparingTo("1500.00");
        assertThat(outpatient.getPatientShare()).isEqualByComparingTo("1500.00");
        assertThat(outpatient.getUsageDetails()).isNotNull();
        assertThat(outpatient.getUsageDetails().getAmountLimit()).isNull();
        assertThat(outpatient.getUsageDetails().getRemainingAmount()).isNull();

        CoverageResult inpatient = calculate(policyId, memberId, pricingItemId, EncounterType.INPATIENT);
        assertThat(inpatient.isNotCovered()).isTrue();
        assertThat(inpatient.getCoveragePercent()).isZero();
        assertThat(inpatient.getUsageDetails()).isNull();
    }

    @Test
    void screenshotScenario_shouldCapBatchAtSixtyThousandAndShowOnlyDirectLimits() {
        Long policyId = requiredLong("select id from benefit_policies where policy_code='POL-2026-001'");
        Long memberId = requiredLong("select min(id) from members where employer_id=1 and benefit_policy_id=" + policyId + " and active=true");

        List<ClaimLineInput> lines = List.of(
                screenshotLine("JAL-TST-016", 6, "100.00"),
                screenshotLine("JAL-TST-017", 1, "900.00"),
                screenshotLine("JAL-TST-003", 5, "20000.00"));
        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(policyId).memberId(memberId).serviceYear(2026)
                .serviceDate(LocalDate.of(2026, 7, 1))
                .encounterType(EncounterType.OUTPATIENT).lines(lines).build();

        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getCompanyShare()).isEqualByComparingTo("600.00");
        assertThat(results.get(1).getCompanyShare()).isEqualByComparingTo("900.00");
        assertThat(results.get(2).getCompanyShare()).isEqualByComparingTo("58500.00");
        assertThat(results.get(2).getLimitRefused()).isEqualByComparingTo("41500.00");
        assertThat(results.stream().map(CoverageResult::getCompanyShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("60000.00");
        assertThat(results.stream().map(CoverageResult::getLimitRefused)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("41500.00");

        assertThat(results.get(0).getUsageDetails().getTimesLimit()).isEqualTo(20);
        assertThat(results.get(0).getUsageDetails().getAmountLimit()).isNull();
        assertThat(results.get(1).getUsageDetails().getAmountLimit()).isEqualByComparingTo("1500.00");
        assertThat(results.get(2).getUsageDetails().getAmountLimit()).isNull();
    }

    private ClaimLineInput screenshotLine(String serviceCode, int quantity, String unitPrice) {
        Long pricingItemId = requiredLong("select id from provider_contract_pricing_items where service_code='"
                + serviceCode + "' and active=true");
        return ClaimLineInput.builder()
                .lineId(serviceCode).pricingItemId(pricingItemId).quantity(quantity)
                .enteredUnitPrice(new BigDecimal(unitPrice))
                .contractPrice(new BigDecimal(unitPrice)).build();
    }

    private CoverageResult calculate(Long policyId, Long memberId, Long pricingItemId, EncounterType encounterType) {
        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("jaliana-live")
                .pricingItemId(pricingItemId)
                .quantity(1)
                .enteredUnitPrice(new BigDecimal("3000.00"))
                .contractPrice(new BigDecimal("3000.00"))
                .build();
        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(policyId)
                .memberId(memberId)
                .serviceYear(2026)
                .serviceDate(LocalDate.of(2026, 7, 21))
                .encounterType(encounterType)
                .lines(List.of(line))
                .build();
        return coverageEngineService.calculateBulk(request).getFirst();
    }

    private Long requiredLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        assertThat(value).as(sql).isNotNull();
        return value;
    }
}
