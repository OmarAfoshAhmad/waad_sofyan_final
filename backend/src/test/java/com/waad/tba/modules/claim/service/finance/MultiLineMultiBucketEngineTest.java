package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.service.ApplicableLimitResolver.ApplicableLimitDefinition;
import com.waad.tba.modules.benefitpolicy.service.EffectiveLimitResolver.EffectiveLimit;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiLineMultiBucketEngineTest {
    private final MultiLineMultiBucketEngine engine = new MultiLineMultiBucketEngine(new WaadFinancialEngine());

    @Test
    void twoLinesCannotReuseTheSameSharedBalance() {
        var group = balance(1L, "BUCKET:GROUP", BenefitScopeType.GROUP, "100.00");
        var service1 = balance(1L, "BUCKET:SERVICE-1", BenefitScopeType.SERVICE, "100.00");
        var service2 = balance(1L, "BUCKET:SERVICE-2", BenefitScopeType.SERVICE, "100.00");

        var result = engine.evaluate(1L, List.of(
                line("L1", set(1L, service1, group)),
                line("L2", set(1L, service2, group))));

        assertThat(result.lines().get(0).financial().insideLimit()).isEqualByComparingTo("60.00");
        assertThat(result.lines().get(1).financial().insideLimit()).isEqualByComparingTo("40.00");
        assertThat(result.lines().get(1).financial().patientLimitExcess()).isEqualByComparingTo("20.00");
        assertThat(result.signedRemainingByLimit().get("BUCKET:GROUP")).isZero();
        assertThat(result.signedRemainingByLimit().get("BUCKET:SERVICE-1")).isEqualByComparingTo("40.00");
        assertThat(result.signedRemainingByLimit().get("BUCKET:SERVICE-2")).isEqualByComparingTo("60.00");
    }

    @Test
    void everyApplicableBucketReceivesTheSameLineConsumptionAndTiesRemainVisible() {
        var service = balance(1L, "SERVICE", BenefitScopeType.SERVICE, "80.00");
        var group = balance(1L, "GROUP", BenefitScopeType.GROUP, "80.00");
        var general = balance(1L, "GENERAL", BenefitScopeType.POLICY_GENERAL, "200.00");

        var result = engine.evaluate(1L, List.of(line("L1", set(1L, service, group, general))));
        var allocations = result.lines().get(0).limitAllocations();

        assertThat(allocations).extracting(MultiLineMultiBucketEngine.LimitAllocation::consumption)
                .allMatch(value -> value.compareTo(new BigDecimal("60.00")) == 0);
        assertThat(allocations).filteredOn(MultiLineMultiBucketEngine.LimitAllocation::binding)
                .extracting(MultiLineMultiBucketEngine.LimitAllocation::semanticKey)
                .containsExactlyInAnyOrder("SERVICE", "GROUP");
    }

    @Test
    void oneClaimWithDifferentCategoriesConsumesSharedGeneralCeilingOnlyOnceAcrossLines() {
        var diagnosticsBucket = balance(1L, "BUCKET:DIAG", BenefitScopeType.GROUP, "3000.00");
        var imagingBucket = balance(1L, "BUCKET:IMG", BenefitScopeType.GROUP, "500.00");
        var inpatientFallbackBucket = balance(1L, "BUCKET:INPATIENT-GENERAL", BenefitScopeType.GROUP, "1000.00");
        var general = balance(1L, "POLICY:GENERAL", BenefitScopeType.POLICY_GENERAL, "800.00");

        var result = engine.evaluate(1L, List.of(
                line("labs", "200.00", 75, set(1L, diagnosticsBucket, general)),
                line("ct", "800.00", 75, set(1L, imagingBucket, general)),
                line("sugar-classified-outpatient-but-claim-inpatient", "100.00", 100,
                        set(1L, inpatientFallbackBucket, general))));

        var labs = result.lines().get(0).financial();
        assertThat(labs.insideLimit()).isEqualByComparingTo("200.00");
        assertThat(labs.insurerFinalPayment()).isEqualByComparingTo("150.00");
        assertThat(labs.patientCoverageShare()).isEqualByComparingTo("50.00");

        var ct = result.lines().get(1).financial();
        assertThat(ct.insideLimit()).isEqualByComparingTo("500.00");
        assertThat(ct.patientLimitExcess()).isEqualByComparingTo("300.00");
        assertThat(ct.insurerFinalPayment()).isEqualByComparingTo("375.00");
        assertThat(ct.patientCoverageShare()).isEqualByComparingTo("125.00");

        var inpatientFallback = result.lines().get(2).financial();
        assertThat(inpatientFallback.insideLimit()).isEqualByComparingTo("100.00");
        assertThat(inpatientFallback.insurerFinalPayment()).isEqualByComparingTo("100.00");
        assertThat(inpatientFallback.patientCoverageShare()).isZero();

        assertThat(result.signedRemainingByLimit().get("POLICY:GENERAL")).isZero();
        assertThat(result.signedRemainingByLimit().get("BUCKET:DIAG")).isEqualByComparingTo("2800.00");
        assertThat(result.signedRemainingByLimit().get("BUCKET:IMG")).isZero();
        assertThat(result.signedRemainingByLimit().get("BUCKET:INPATIENT-GENERAL")).isEqualByComparingTo("900.00");
    }

    @Test
    void rejectsBalancesBelongingToAnotherFamilyMember() {
        assertThatThrownBy(() -> engine.evaluate(1L,
                List.of(line("L1", set(2L, balance(2L, "GROUP", BenefitScopeType.GROUP, "100.00"))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINE_BALANCE_MEMBER_MISMATCH");
    }

    @Test
    void rejectsTwoDifferentStartingBalancesForTheSameSemanticLimit() {
        var first = balance(1L, "GROUP", BenefitScopeType.GROUP, "100.00");
        var second = balance(1L, "GROUP", BenefitScopeType.GROUP, "90.00");
        assertThatThrownBy(() -> engine.evaluate(1L,
                List.of(line("L1", set(1L, first)), line("L2", set(1L, second)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INCONSISTENT_LIMIT_SNAPSHOT");
    }

    private MultiLineMultiBucketEngine.LineInput line(String key, LimitBalanceReader.BalanceSet balances) {
        return new MultiLineMultiBucketEngine.LineInput(key, new BigDecimal("60.00"),
                new BigDecimal("60.00"), 80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1, balances);
    }

    private MultiLineMultiBucketEngine.LineInput line(String key, String amount, int coveragePercent,
                                                      LimitBalanceReader.BalanceSet balances) {
        return new MultiLineMultiBucketEngine.LineInput(key, new BigDecimal(amount),
                new BigDecimal(amount), coveragePercent, BigDecimal.ZERO, BigDecimal.ZERO, false, 1, balances);
    }

    private LimitBalanceReader.BalanceSet set(Long memberId, LimitBalanceReader.LimitBalance... balances) {
        BigDecimal binding = java.util.Arrays.stream(balances)
                .map(LimitBalanceReader.LimitBalance::reservableAvailable).min(BigDecimal::compareTo).orElse(null);
        return new LimitBalanceReader.BalanceSet(memberId, List.of(balances), binding, List.of());
    }

    private LimitBalanceReader.LimitBalance balance(Long memberId, String key, BenefitScopeType scope,
                                                     String available) {
        BigDecimal limitValue = new BigDecimal(available);
        var definition = new ApplicableLimitDefinition(key, scope, BeneficiaryScopeType.MEMBER,
                scope == BenefitScopeType.POLICY_GENERAL ? null : Math.abs((long) key.hashCode()),
                10L, 20L, null, limitValue, "ANNUAL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1, 0);
        var effective = new EffectiveLimit(definition, limitValue, SourceType.POLICY_DEFAULT, null, null);
        // no committed, no reserved: both balances equal the full limit.
        // The occurrence dimension is null -- this bucket declares no times
        // limit, which is not the same as having none left.
        return new LimitBalanceReader.LimitBalance(effective, BigDecimal.ZERO, BigDecimal.ZERO,
                limitValue, limitValue, null, null, null, null, null);
    }
}
