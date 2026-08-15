package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.claim.projection.MemberFinancialAggregateProjection;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.dto.CoverageLimitsDto;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock
    private MemberPolicyResolver memberPolicyResolver;

    @InjectMocks
    private MemberFinancialSummaryService service;

    @BeforeEach
    void resolveThePolicyOnTheSummaryDate() {
        org.mockito.Mockito.lenient()
                .when(memberPolicyResolver.resolveFor(any(Member.class), any(LocalDate.class)))
                .thenAnswer(invocation -> Optional.ofNullable(
                        invocation.<Member>getArgument(0).getBenefitPolicy()));
    }

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

    // ==================== getServiceCoverageLimits (member-closure Phase 4) ====================
    // Times-used previously came from loading every claim for the member and counting matches in
    // a Java loop; it now comes from one COUNT query (ClaimRepository.countServiceUsageForMemberAndYear).
    // These tests pin the exact arithmetic and prove no claim entity is ever touched.

    private Member plainMember(Long id) {
        return Member.builder().id(id).fullName("Member " + id).build();
    }

    private MedicalService serviceWithCode(Long id, String code) {
        return MedicalService.builder().id(id).code(code).name("Consultation").active(true).build();
    }

    @Test
    @DisplayName("getServiceCoverageLimits reads times-used from a single COUNT query, not from loaded claims")
    void getServiceCoverageLimits_TimesUsedComesFromCountQuery_NotFromLoadedClaims() {
        Member member = plainMember(1L);
        MedicalService svc = serviceWithCode(101L, "SVC-1");
        when(memberRepository.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(medicalServiceRepository.findByCode("SVC-1")).thenReturn(java.util.Optional.of(svc));
        when(coverageService.getCoverageForService(member, 101L)).thenReturn(java.util.Optional.of(
                BenefitPolicyCoverageService.CoverageInfo.builder()
                        .covered(true).coveragePercent(80).amountLimit(new BigDecimal("500.00")).timesLimit(3)
                        .build()));
        when(claimRepository.countServiceUsageForMemberAndYear(eq(1L), eq("SVC-1"), anyInt())).thenReturn(2L);

        CoverageLimitsDto result = service.getServiceCoverageLimits(1L, "SVC-1");

        assertThat(result.isCovered()).isTrue();
        assertThat(result.getTimesUsed()).isEqualTo(2);
        assertThat(result.getRemainingTimes()).isEqualTo(1); // 3 - 2
        assertThat(result.isTimesLimitExceeded()).isFalse();
        verify(claimRepository, never()).findByMemberId(anyLong());
    }

    @Test
    @DisplayName("getServiceCoverageLimits marks the limit exceeded and clamps remaining at zero, never negative")
    void getServiceCoverageLimits_TimesLimitExceeded_ClampsRemainingAtZero() {
        Member member = plainMember(1L);
        MedicalService svc = serviceWithCode(101L, "SVC-1");
        when(memberRepository.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(medicalServiceRepository.findByCode("SVC-1")).thenReturn(java.util.Optional.of(svc));
        when(coverageService.getCoverageForService(member, 101L)).thenReturn(java.util.Optional.of(
                BenefitPolicyCoverageService.CoverageInfo.builder()
                        .covered(true).coveragePercent(80).timesLimit(2).build()));
        when(claimRepository.countServiceUsageForMemberAndYear(eq(1L), eq("SVC-1"), anyInt())).thenReturn(5L);

        CoverageLimitsDto result = service.getServiceCoverageLimits(1L, "SVC-1");

        assertThat(result.getTimesUsed()).isEqualTo(5);
        assertThat(result.getRemainingTimes()).isZero();
        assertThat(result.isTimesLimitExceeded()).isTrue();
        assertThat(result.getWarningMessage()).contains("تجاوز الحد الأقصى");
    }

    @Test
    @DisplayName("getServiceCoverageLimits skips the times-used query entirely when the service has no times limit")
    void getServiceCoverageLimits_NoTimesLimit_SkipsCountQuery() {
        Member member = plainMember(1L);
        MedicalService svc = serviceWithCode(101L, "SVC-1");
        when(memberRepository.findById(1L)).thenReturn(java.util.Optional.of(member));
        when(medicalServiceRepository.findByCode("SVC-1")).thenReturn(java.util.Optional.of(svc));
        when(coverageService.getCoverageForService(member, 101L)).thenReturn(java.util.Optional.of(
                BenefitPolicyCoverageService.CoverageInfo.builder()
                        .covered(true).coveragePercent(80).timesLimit(null).build()));

        CoverageLimitsDto result = service.getServiceCoverageLimits(1L, "SVC-1");

        assertThat(result.getTimesLimit()).isNull();
        assertThat(result.isTimesLimitExceeded()).isFalse();
        verify(claimRepository, never()).countServiceUsageForMemberAndYear(anyLong(), anyString(), anyInt());
    }
}
