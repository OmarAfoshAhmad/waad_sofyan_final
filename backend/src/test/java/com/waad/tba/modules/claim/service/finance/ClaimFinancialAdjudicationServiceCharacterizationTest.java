package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.EffectiveLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.service.finance.MultiLineMultiBucketEngine.ClaimResult;
import com.waad.tba.modules.claim.service.finance.MultiLineMultiBucketEngine.LineResult;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.preauthorization.repository.PreauthDecisionSnapshotRepository;

/**
 * ADR-008 P0: characterizes a defect that MUST be fixed by the P1 engine
 * unification, so the fix can later prove it removed exactly this and
 * nothing else (closure protocol §33: "old behavior documented as
 * incorrect, new behavior business-approved").
 *
 * Proven by the coverage-core audit (2026-09-04): ClaimFinancialAdjudicationService
 * re-derives money from ClaimLine.requestedTotal/contractUnitPrice and
 * unconditionally overwrites companyShare/patientShare/limitRefused with its
 * own result -- discarding whatever CoverageEngineService (the times/days-aware
 * engine that runs first, in ClaimMapper) had already decided. A times-limit
 * refusal computed by the first engine therefore never reaches the money
 * saved on the claim.
 *
 * This is NOT a test of correct behavior. It is a lock on the current,
 * known-wrong behavior, so P1 can delete this test with a comment pointing
 * at the commit that fixed it -- not silently change what it asserts.
 */
@ExtendWith(MockitoExtension.class)
class ClaimFinancialAdjudicationServiceCharacterizationTest {

    @Mock BenefitPolicyRepository policyRepository;
    @Mock MemberPolicyResolver memberPolicyResolver;
    @Mock PreauthDecisionSnapshotRepository decisionSnapshotRepository;
    @Mock EffectiveLimitResolver effectiveLimitResolver;
    @Mock LimitBalanceReader balanceReader;
    @Mock MultiLineMultiBucketEngine multiLineEngine;

    private ClaimFinancialAdjudicationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimFinancialAdjudicationService(policyRepository, memberPolicyResolver,
                decisionSnapshotRepository, effectiveLimitResolver, balanceReader, multiLineEngine);
    }

    @Test
    @DisplayName("CHARACTERIZATION (known defect): a times-limit refusal already decided upstream is silently overwritten to zero")
    void engineBOverwritesAnUpstreamTimesLimitRefusalWithItsOwnUnrelatedResult() {
        Member member = Member.builder().id(500L).build();
        Claim claim = Claim.builder().member(member).serviceDate(LocalDate.of(2026, 3, 1)).build();

        ClaimLine line = new ClaimLine();
        line.setAppliedRuleId(900L);
        line.setCoveragePercentSnapshot(100);
        line.setRequestedTotal(new BigDecimal("500.00"));
        line.setContractUnitPrice(new BigDecimal("100.00"));
        line.setQuantity(5);
        // This is what CoverageEngineService (engine A, runs first in
        // ClaimMapper) had already decided: a times-limit exceeded 3 of the
        // 5 requested sessions, refusing 300.00 of the line.
        line.setLimitRefused(new BigDecimal("300.00"));
        line.setCompanyShare(new BigDecimal("200.00"));
        claim.setLines(List.of(line));

        BenefitPolicy policy = BenefitPolicy.builder().id(700L).build();
        when(memberPolicyResolver.resolveForOrFail(member, claim.getServiceDate())).thenReturn(policy);
        when(effectiveLimitResolver.resolve(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of());
        when(balanceReader.read(anyLong(), any(), any()))
                .thenReturn(new LimitBalanceReader.BalanceSet(500L, List.of(), null, List.of()));

        // Engine B (WaadFinancialEngine, via MultiLineMultiBucketEngine) has
        // NO occurrence dimension at all -- it only ever sees requestedTotal
        // and contractUnitPrice, never the times-limit refusal. Here it
        // finds nothing binding and would fully approve the line.
        WaadFinancialEngine.Result fullyApproved = new WaadFinancialEngine.Result(
                new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("500.00"), WaadFinancialEngine.LimitMode.UNLIMITED, null,
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"), null,
                100, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("500.00"));
        LineResult lineResult = new LineResult("INDEX:0", fullyApproved, List.of());
        when(multiLineEngine.evaluate(anyLong(), any()))
                .thenReturn(new ClaimResult(500L, List.of(lineResult), java.util.Map.of()));

        service.adjudicate(claim);

        // The defect, pinned: the 300.00 times-limit refusal engine A had
        // already decided is gone. Engine B's "fully approved, no limit
        // bound" answer wins, silently.
        assertThat(line.getLimitRefused()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.getCompanyShare()).isEqualByComparingTo("500.00");
    }
}
