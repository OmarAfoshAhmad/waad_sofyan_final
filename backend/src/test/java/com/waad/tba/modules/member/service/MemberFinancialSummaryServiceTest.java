package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.projection.MemberFinancialAggregateProjection;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * member-closure P0: proves the exact defect this rewrite fixed can never
 * silently return -- the member window's "used"/"remaining" must read the
 * WAAD-FIN-1.0 limit-consumption axis, never totalApproved (which are
 * different numbers by construction: a limit is consumed before coverage
 * split, contract discount, and rejection are applied on top of it). Also
 * proves the bulk path is genuinely O(1) queries, not a per-member loop
 * wearing a bulk-looking signature.
 */
@ExtendWith(MockitoExtension.class)
class MemberFinancialSummaryServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BenefitPolicyCoverageService coverageService;
    @Mock
    private BenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private MedicalServiceRepository medicalServiceRepository;

    @InjectMocks
    private MemberFinancialSummaryService service;

    private Member memberWithPolicy(Long id, BigDecimal annualLimit) {
        BenefitPolicy policy = BenefitPolicy.builder()
                .id(100L).name("Gold").status(BenefitPolicyStatus.ACTIVE).active(true)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .annualLimit(annualLimit).build();
        return Member.builder().id(id).fullName("Member " + id).benefitPolicy(policy).build();
    }

    private MemberFinancialAggregateProjection stats(Long memberId, BigDecimal totalApproved) {
        return new MemberFinancialAggregateProjection() {
            @Override public Long getMemberId() { return memberId; }
            @Override public Long getClaimsCount() { return 5L; }
            @Override public Long getPendingClaimsCount() { return 1L; }
            @Override public Long getApprovedClaimsCount() { return 3L; }
            @Override public Long getRejectedClaimsCount() { return 1L; }
            @Override public BigDecimal getTotalClaimed() { return new BigDecimal("1200.00"); }
            @Override public BigDecimal getTotalApproved() { return totalApproved; }
            @Override public BigDecimal getTotalPaid() { return BigDecimal.ZERO; }
            @Override public BigDecimal getTotalPatientCoPay() { return new BigDecimal("120.00"); }
            @Override public BigDecimal getTotalDeductibleApplied() { return BigDecimal.ZERO; }
            @Override public LocalDateTime getLastClaimAt() { return LocalDateTime.of(2026, 6, 1, 10, 0); }
        };
    }

    @Test
    @DisplayName("usedAmount/remaining must come from limit consumption, not approvedAmount -- the exact P0 this rewrite fixed")
    void financialSummary_UsesLimitConsumptionAxis_NotApprovedAmount() {
        Member member = memberWithPolicy(1L, new BigDecimal("1000.00"));
        when(memberRepository.findAllById(anyCollection())).thenReturn(List.of(member));
        // 382.00 is the golden-scenario insurerFinalPayment (approvedAmount); the
        // true limit consumption for that same claim is 600.00 (WAAD-FIN-1.0's own
        // golden test). If the member window ever reads totalApproved again, this
        // assertion catches it immediately.
        when(claimRepository.findFinancialAggregatesByMemberIds(anyCollection()))
                .thenReturn(List.of(stats(1L, new BigDecimal("382.00"))));
        when(coverageService.getLimitConsumedForYear(anyCollection(), anyInt(), any()))
                .thenReturn(Map.of(1L, new BigDecimal("600.00")));

        MemberFinancialSummaryDto summary = service.getFinancialSummary(1L);

        assertThat(summary.getTotalApproved()).isEqualByComparingTo("382.00");
        assertThat(summary.getLimitConsumedAmount()).isEqualByComparingTo("600.00");
        assertThat(summary.getRemainingCoverage()).isEqualByComparingTo("400.00"); // 1000 - 600, not 1000 - 382
        assertThat(summary.getUtilizationPercent()).isEqualByComparingTo("60.00"); // 600/1000, not 382/1000
    }

    @Test
    @DisplayName("Bulk read for N members costs exactly one claim-stats query and one consumption query, not N")
    void financialSummaries_BulkRead_IsOneQueryPerAggregate() {
        Member principal = memberWithPolicy(1L, new BigDecimal("2000.00"));
        Member dependent = memberWithPolicy(2L, new BigDecimal("2000.00"));
        when(memberRepository.findAllById(anyCollection())).thenReturn(List.of(principal, dependent));
        when(claimRepository.findFinancialAggregatesByMemberIds(anyCollection()))
                .thenReturn(List.of(stats(1L, new BigDecimal("100.00")), stats(2L, new BigDecimal("50.00"))));
        when(coverageService.getLimitConsumedForYear(anyCollection(), anyInt(), any()))
                .thenReturn(Map.of(1L, new BigDecimal("150.00"), 2L, new BigDecimal("70.00")));

        Map<Long, MemberFinancialSummaryDto> result = service.getFinancialSummaries(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(1L).getLimitConsumedAmount()).isEqualByComparingTo("150.00");
        assertThat(result.get(2L).getLimitConsumedAmount()).isEqualByComparingTo("70.00");
        verify(claimRepository, times(1)).findFinancialAggregatesByMemberIds(anyCollection());
        verify(coverageService, times(1)).getLimitConsumedForYear(anyCollection(), anyInt(), any());
    }

    @Test
    @DisplayName("A member with no claims yet gets zeroed metrics, not a null-pointer")
    void financialSummary_MemberWithNoClaims_ReturnsZeroedMetrics() {
        Member member = memberWithPolicy(1L, new BigDecimal("1000.00"));
        when(memberRepository.findAllById(anyCollection())).thenReturn(List.of(member));
        when(claimRepository.findFinancialAggregatesByMemberIds(anyCollection())).thenReturn(List.of());
        when(coverageService.getLimitConsumedForYear(anyCollection(), anyInt(), any()))
                .thenReturn(Map.of(1L, BigDecimal.ZERO));

        MemberFinancialSummaryDto summary = service.getFinancialSummary(1L);

        assertThat(summary.getClaimsCount()).isZero();
        assertThat(summary.getTotalApproved()).isEqualByComparingTo("0.00");
        assertThat(summary.getLimitConsumedAmount()).isEqualByComparingTo("0.00");
        assertThat(summary.getRemainingCoverage()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Unknown member fails closed with ResourceNotFoundException, not a silently empty summary")
    void financialSummary_UnknownMember_ThrowsNotFound() {
        when(memberRepository.findAllById(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.getFinancialSummary(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Member with no policy skips limit metrics entirely instead of dividing by a null/zero annualLimit")
    void financialSummary_NoPolicy_SkipsLimitMetrics() {
        Member member = Member.builder().id(1L).fullName("No Policy").benefitPolicy(null).build();
        when(memberRepository.findAllById(anyCollection())).thenReturn(List.of(member));
        when(claimRepository.findFinancialAggregatesByMemberIds(anyCollection())).thenReturn(List.of());
        when(coverageService.getLimitConsumedForYear(anyCollection(), anyInt(), any()))
                .thenReturn(Map.of(1L, BigDecimal.ZERO));

        MemberFinancialSummaryDto summary = service.getFinancialSummary(1L);

        assertThat(summary.getPolicyActive()).isFalse();
        assertThat(summary.getAnnualLimit()).isNull();
        assertThat(summary.getRemainingCoverage()).isNull();
        assertThat(summary.getWarningMessage()).contains("لا توجد وثيقة تغطية");
    }

    @Test
    @DisplayName("Empty member id collection short-circuits without touching either repository")
    void financialSummaries_EmptyInput_SkipsAllQueries() {
        Map<Long, MemberFinancialSummaryDto> result = service.getFinancialSummaries(List.of());

        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(memberRepository, claimRepository, coverageService);
    }
}
