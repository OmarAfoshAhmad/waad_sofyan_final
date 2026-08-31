package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    @Test
    void darAlShifaMaternityScenario_enforcesIndividualAndGeneralCeilingsOnDifferentAxes() {
        Long policyId = requiredLong("select id from benefit_policies where policy_code='POL-2026-001'");
        Long memberId = requiredLong("select min(id) from members where employer_id=1 and benefit_policy_id=" + policyId + " and active=true");
        ClaimLineInput caesarean = pricedLine(101L, "F-006", 1);
        ClaimLineInput ventilator = pricedLine(101L, "WE-001", 10);

        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(policyId).memberId(memberId).serviceYear(2026)
                .serviceDate(LocalDate.of(2026, 8, 31)).encounterType(EncounterType.INPATIENT)
                .claimContextCode("MATERNITY").lines(List.of(caesarean, ventilator)).build();

        List<CoverageResult> results = coverageEngineService.calculateBulk(request);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCompanyShare()).isEqualByComparingTo("1237.50");
        assertThat(results.get(0).getPatientShare()).isEqualByComparingTo("412.50");
        assertThat(results.get(1).getLimitRefused()).isEqualByComparingTo("650.00");
        assertThat(results.get(1).getCompanyShare()).isEqualByComparingTo("1762.50");
        assertThat(results.get(1).getPatientShare()).isEqualByComparingTo("587.50");
        assertThat(results.get(1).getUsageDetails().getConsumptionBasis()).isEqualTo("ELIGIBLE_AMOUNT");
        assertThat(results.get(1).getUsageDetails().getUsedAmountBeforeLine()).isEqualByComparingTo("1650.00");
        assertThat(results.get(1).getUsageDetails().getRequestedAmountForLimit()).isEqualByComparingTo("3000.00");
        assertThat(results.stream().map(CoverageResult::getCompanyShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("3000.00");
    }

    @Test
    void everyObservedProviderClassificationHasAnEffectiveBaseContextDecision() {
        Long policyId = requiredLong("select id from benefit_policies where policy_code='POL-2026-001'");
        Long memberId = requiredLong("select min(id) from members where employer_id=1 and benefit_policy_id=" + policyId + " and active=true");
        List<Map<String, Object>> representatives = jdbcTemplate.queryForList("""
                select distinct on (i.contract_id, i.medical_category_id, i.claim_context_code)
                       i.contract_id, i.id pricing_item_id, i.claim_context_code, c.code category_code
                  from provider_contract_pricing_items i
                  join medical_categories c on c.id=i.medical_category_id
                 where i.contract_id in (1,51,101) and i.active=true
                 order by i.contract_id, i.medical_category_id, i.claim_context_code, i.id
                """);

        assertThat(representatives).hasSize(21);
        for (Map<String, Object> representative : representatives) {
            Long pricingItemId = ((Number) representative.get("pricing_item_id")).longValue();
            String contextCode = String.valueOf(representative.get("claim_context_code"));
            EncounterType encounter = EncounterType.valueOf(contextCode);
            ClaimLineInput line = ClaimLineInput.builder().lineId("matrix-" + pricingItemId)
                    .pricingItemId(pricingItemId).quantity(1)
                    .enteredUnitPrice(BigDecimal.ONE).contractPrice(BigDecimal.ONE).build();
            CoverageResult result = coverageEngineService.calculateBulk(BulkCoverageEngineRequest.builder()
                    .policyId(policyId).memberId(memberId).serviceYear(2026)
                    .serviceDate(LocalDate.of(2026, 8, 31)).encounterType(encounter)
                    .claimContextCode(contextCode).lines(List.of(line)).build()).getFirst();

            assertThat(result.isNotCovered())
                    .as("contract=%s category=%s context=%s", representative.get("contract_id"),
                            representative.get("category_code"), contextCode)
                    .isFalse();
            assertThat(result.getCoveragePercent()).isBetween(1, 100);
        }
    }

    @Test
    void kayanCosmeticDentalIsUncoveredWhileRoutineDentalKeepsItsConfiguredShare() {
        Long policyId = requiredLong("select id from benefit_policies where policy_code='POL-2026-001'");
        Long memberId = requiredLong("select min(id) from members where employer_id=1 and benefit_policy_id=" + policyId + " and active=true");
        Long cosmeticId = requiredLong("select id from provider_contract_pricing_items where contract_id=151 and service_code='F1' and active=true");
        Long routineId = requiredLong("select id from provider_contract_pricing_items where contract_id=151 and service_code='D1' and active=true");

        CoverageResult cosmetic = calculateAtContractPrice(policyId, memberId, cosmeticId);
        assertThat(cosmetic.isNotCovered()).isTrue();
        assertThat(cosmetic.getCoveragePercent()).isZero();
        assertThat(cosmetic.getCompanyShare()).isEqualByComparingTo("0.00");
        assertThat(cosmetic.getPatientShare()).isEqualByComparingTo("50.00");

        CoverageResult routine = calculateAtContractPrice(policyId, memberId, routineId);
        assertThat(routine.isNotCovered()).isFalse();
        assertThat(routine.getCoveragePercent()).isEqualTo(75);
        assertThat(routine.getCompanyShare()).isEqualByComparingTo("7.50");
        assertThat(routine.getPatientShare()).isEqualByComparingTo("2.50");
    }

    private ClaimLineInput pricedLine(Long contractId, String serviceCode, int quantity) {
        Long pricingItemId = requiredLong("select id from provider_contract_pricing_items where contract_id="
                + contractId + " and service_code='" + serviceCode + "' and active=true");
        BigDecimal price = jdbcTemplate.queryForObject(
                "select contract_price from provider_contract_pricing_items where id=?", BigDecimal.class, pricingItemId);
        return ClaimLineInput.builder().lineId(serviceCode).pricingItemId(pricingItemId).quantity(quantity)
                .enteredUnitPrice(price).contractPrice(price).build();
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

    private CoverageResult calculateAtContractPrice(Long policyId, Long memberId, Long pricingItemId) {
        BigDecimal price = jdbcTemplate.queryForObject(
                "select contract_price from provider_contract_pricing_items where id=?", BigDecimal.class, pricingItemId);
        ClaimLineInput line = ClaimLineInput.builder().lineId("kayan-" + pricingItemId)
                .pricingItemId(pricingItemId).quantity(1)
                .enteredUnitPrice(price).contractPrice(price).build();
        return coverageEngineService.calculateBulk(BulkCoverageEngineRequest.builder()
                .policyId(policyId).memberId(memberId).serviceYear(2026)
                .serviceDate(LocalDate.of(2026, 8, 31)).encounterType(EncounterType.OUTPATIENT)
                .claimContextCode("OUTPATIENT").lines(List.of(line)).build()).getFirst();
    }

    private Long requiredLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        assertThat(value).as(sql).isNotNull();
        return value;
    }
}
