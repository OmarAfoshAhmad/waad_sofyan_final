package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.service.EligibilityEngineService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * Characterization tests for the family-eligibility consolidation:
 * checkFamilyEligibility(barcode) used to compute its own shallow
 * active+cachedEligibilityFlag+hasEmployer check and never consulted the
 * real eligibility engine (the same one modules/eligibility.
 * FamilyEligibilityService uses) -- so this "PRIMARY eligibility check
 * method" (per its own API docs) could disagree with the engine for the
 * same member. It now delegates the actual eligible/not-eligible decision
 * to EligibilityEngineService per family member. It also used to swallow
 * any financial-summary lookup failure entirely, returning a response with
 * no signal that limit data was unavailable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnifiedMemberServiceFamilyEligibilityTest {

    @Mock private MemberRepository memberRepository;
    @Mock private UnifiedMemberMapper mapper;
    @Mock private MemberFinancialSummaryService financialSummaryService;
    @Mock private EligibilityEngineService eligibilityEngineService;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UnifiedMemberService service;

    private Member principal;

    private void setUpPrincipal() {
        principal = Member.builder()
                .id(100L)
                .fullName("Ali Hasan")
                .barcode("WAHA-2026-000100")
                .employer(Employer.builder().id(1L).name("Employer One").build())
                .parent(null)
                .build();

        when(memberRepository.findByBarcode("WAHA-2026-000100")).thenReturn(java.util.Optional.of(principal));
        when(memberRepository.findByParentId(100L)).thenReturn(List.of());

        FamilyEligibilityResponseDto mappedResponse = FamilyEligibilityResponseDto.builder()
                .principal(MemberViewDto.builder().id(100L).build())
                .dependents(List.of())
                .totalFamilyMembers(1)
                .build();
        when(mapper.toFamilyEligibilityResponse(org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.eq(List.of()), any()))
                .thenReturn(mappedResponse);

        MemberFinancialSummaryDto summary = MemberFinancialSummaryDto.builder()
                .memberId(100L)
                .annualLimit(new BigDecimal("50000"))
                .remainingCoverage(new BigDecimal("42000"))
                .limitConsumedAmount(new BigDecimal("8000"))
                .build();
        when(financialSummaryService.getFinancialSummaries(any())).thenReturn(Map.of(100L, summary));
    }

    @Test
    void engineRejectionMakesFamilyIneligibleEvenIfLegacyFlagsWouldHaveAllowedIt() {
        setUpPrincipal();
        // The engine rejects this member (e.g. exhausted limit) -- the old
        // shallow active+cachedFlag+hasEmployer check would have had no way
        // to know this and could have reported the family eligible anyway.
        when(eligibilityEngineService.checkEligibility(any(com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest.class)))
                .thenReturn(EligibilityResult.notEligible("req-1", null,
                        List.of(EligibilityResult.ReasonDetail.builder()
                                .code("LIMIT_EXHAUSTED").messageAr("تم استنفاد السقف السنوي").hardFailure(true).build()),
                        5L, 3));

        service.checkFamilyEligibility("WAHA-2026-000100");

        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(mapper).toFamilyEligibilityResponse(
                org.mockito.ArgumentMatchers.eq(principal), org.mockito.ArgumentMatchers.eq(List.of()), captor.capture());

        @SuppressWarnings("unchecked")
        Map<Long, EligibilityResult> resultsPassedToMapper = captor.getValue();
        assertThat(resultsPassedToMapper.get(100L).isEligible()).isFalse();
    }

    @Test
    void engineIsConsultedPerFamilyMember() {
        setUpPrincipal();
        when(eligibilityEngineService.checkEligibility(any(com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest.class)))
                .thenReturn(EligibilityResult.eligible("req-2", null, 5L, 3));

        service.checkFamilyEligibility("WAHA-2026-000100");

        org.mockito.Mockito.verify(eligibilityEngineService).checkEligibility(
                org.mockito.ArgumentMatchers.argThat(
                        (com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest req) -> req.getMemberId().equals(100L)));
    }

    @Test
    void engineFailureForOneMemberFailsClosedForThatMemberOnly() {
        setUpPrincipal();
        when(eligibilityEngineService.checkEligibility(any(com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest.class))).thenThrow(new RuntimeException("engine down"));

        service.checkFamilyEligibility("WAHA-2026-000100");

        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(mapper).toFamilyEligibilityResponse(any(), any(), captor.capture());

        @SuppressWarnings("unchecked")
        Map<Long, EligibilityResult> resultsPassedToMapper = captor.getValue();
        assertThat(resultsPassedToMapper.get(100L).isEligible()).isFalse();
    }

    @Test
    void financialLookupFailureIsSurfacedExplicitlyNotSwallowed() {
        setUpPrincipal();
        when(eligibilityEngineService.checkEligibility(any(com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest.class)))
                .thenReturn(EligibilityResult.eligible("req-3", null, 5L, 3));
        when(financialSummaryService.getFinancialSummaries(any())).thenThrow(new RuntimeException("db timeout"));

        FamilyEligibilityResponseDto response = service.checkFamilyEligibility("WAHA-2026-000100");

        assertThat(response.getFinancialDataAvailable()).isFalse();
        assertThat(response.getFinancialDataError()).isNotBlank();
    }

    @Test
    void financialLookupSuccessLeavesDataAvailableTrue() {
        setUpPrincipal();
        when(eligibilityEngineService.checkEligibility(any(com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest.class)))
                .thenReturn(EligibilityResult.eligible("req-4", null, 5L, 3));

        FamilyEligibilityResponseDto response = service.checkFamilyEligibility("WAHA-2026-000100");

        assertThat(response.getFinancialDataAvailable()).isTrue();
        assertThat(response.getFinancialDataError()).isNull();
    }
}
