package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialInvariantGuard;
import com.waad.tba.modules.claim.service.finance.ClaimFinancialAdjudicationService;
import com.waad.tba.modules.claim.service.finance.ClaimLimitSnapshotFactory;
import com.waad.tba.modules.benefitpolicy.service.ClaimLimitSnapshotService;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * finance-00 step 4: finalizeSnapshot must be commit-and-validate ONLY -- it
 * must never recompute a financial field, only lock the member, run GUARD 2,
 * and validate the annual ceiling against the number ClaimMapper already
 * computed. This replaces the pre-step-4 version of this test, which
 * exercised the old (buggy) CostCalculationService-based recompute that this
 * class no longer performs at all.
 */
@ExtendWith(MockitoExtension.class)
class ClaimFinancialSnapshotServiceTest {

    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock
    private ClaimFinancialInvariantGuard claimFinancialInvariantGuard;
    @Mock
    private ClaimFinancialAdjudicationService financialAdjudicationService;
    @Mock
    private ClaimLimitSnapshotFactory limitSnapshotFactory;
    @Mock
    private ClaimLimitSnapshotService limitSnapshotService;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private ClaimFinancialSnapshotService service;

    private ClaimLine balancedLine() {
        return ClaimLine.builder()
                .requestedTotal(new BigDecimal("1000.00"))
                .companyShare(new BigDecimal("648.00"))
                .patientShare(new BigDecimal("180.00"))
                .refusedAmount(new BigDecimal("172.00"))
                // Deliberately distinct from companyShare: proves the ceiling check
                // reads ClaimFinancialTotals.sumLimitConsumption (WAAD-FIN-1.0 S4's
                // axis), not approvedAmount -- the two would be indistinguishable in
                // this test if they happened to share a value.
                .limitConsumption(new BigDecimal("700.00"))
                .build();
    }

    private Claim balancedClaim(Member member) {
        Claim claim = Claim.builder()
                .id(1L)
                .member(member)
                .serviceDate(LocalDate.of(2026, 1, 1))
                .requestedAmount(new BigDecimal("1000.00"))
                .approvedAmount(new BigDecimal("648.00"))
                .netProviderAmount(new BigDecimal("648.00"))
                .patientCoPay(new BigDecimal("180.00"))
                .refusedAmount(new BigDecimal("172.00"))
                .companyDiscountAmount(new BigDecimal("0.00"))
                .lines(List.of(balancedLine()))
                .build();
        return claim;
    }

    @Test
    void finalizeSnapshotNeverRewritesAnyFieldItReturnsExactlyWhatWasAlreadyCorrect() {
        Member member = Member.builder().id(10L).build();
        Claim claim = balancedClaim(member);
        when(memberRepository.findByIdWithLock(10L)).thenReturn(Optional.of(member));

        BigDecimal payable = service.finalizeSnapshot(claim);

        assertThat(payable).isEqualByComparingTo("648.00");
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("648.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("648.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("180.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("172.00");
    }

    @Test
    void finalizeSnapshotLocksTheMemberBeforeValidatingTheCeiling() {
        Member member = Member.builder().id(10L)
                .benefitPolicy(BenefitPolicy.builder().id(99L).annualLimit(new BigDecimal("5000.00")).build())
                .build();
        Claim claim = balancedClaim(member);
        when(memberRepository.findByIdWithLock(10L)).thenReturn(Optional.of(member));

        service.finalizeSnapshot(claim);

        verify(memberRepository, times(1)).findByIdWithLock(10L);
        verify(claimFinancialInvariantGuard, times(1)).assertConsistent(claim);
        verify(benefitPolicyCoverageService, times(1)).validateAmountLimits(
                eq(member), eq(member.getBenefitPolicy()), eq(new BigDecimal("700.00")),
                eq(claim.getServiceDate()), eq(claim.getId()));
    }

    @Test
    void finalizeSnapshotSkipsTheCeilingCheckWhenTheMemberHasNoBenefitPolicy() {
        Member member = Member.builder().id(10L).benefitPolicy(null).build();
        Claim claim = balancedClaim(member);
        when(memberRepository.findByIdWithLock(10L)).thenReturn(Optional.of(member));

        service.finalizeSnapshot(claim);

        verify(benefitPolicyCoverageService, never()).validateAmountLimits(
                any(), any(), any(), any(), anyLong());
    }

    @Test
    void finalizeSnapshotFailsClosedWhenTheClaimTotalsDisagreeWithItsOwnLines() {
        Member member = Member.builder().id(10L).build();
        Claim claim = balancedClaim(member);
        when(memberRepository.findByIdWithLock(10L)).thenReturn(Optional.of(member));
        org.mockito.Mockito.doThrow(new BusinessRuleException("mismatch"))
                .when(claimFinancialInvariantGuard).assertConsistent(claim);

        assertThatThrownBy(() -> service.finalizeSnapshot(claim))
                .isInstanceOf(BusinessRuleException.class);

        // The ceiling must never be checked against numbers GUARD 2 already
        // rejected -- fail closed means stop, not proceed with a bad number.
        verify(benefitPolicyCoverageService, never()).validateAmountLimits(
                any(), any(), any(), any(), anyLong());
    }
}
