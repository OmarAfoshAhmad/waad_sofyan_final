package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.service.FamilyEligibilityService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;

/**
 * Characterization tests for UnifiedMemberService.checkFamilyEligibility
 * after the family-eligibility consolidation. It used to compute its own
 * shallow active+cachedEligibilityFlag+hasEmployer check AND run its own
 * copy of the per-member engine-calling loop -- both duplicating
 * modules/eligibility.FamilyEligibilityService, which did the same thing
 * for a different endpoint (memberId-based instead of barcode-based) with
 * its own separate loop and always-today date instead of a caller-supplied
 * serviceDate.
 *
 * Now this method's only job is: resolve barcode -> principal, delegate
 * family resolution and evaluation entirely to FamilyEligibilityService
 * (the single shared orchestrator, see FamilyEligibilityServiceTest for its
 * own coverage), map the result, and enrich with financial data -- so these
 * tests verify DELEGATION and the financial-failure handling, not the
 * eligibility decision logic itself (that's tested once, at its source).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnifiedMemberServiceFamilyEligibilityTest {

    @Mock private MemberRepository memberRepository;
    @Mock private UnifiedMemberMapper mapper;
    @Mock private MemberFinancialSummaryService financialSummaryService;
    @Mock private FamilyEligibilityService familyEligibilityService;
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

        when(familyEligibilityService.resolveFamily(principal))
                .thenReturn(new FamilyEligibilityService.FamilyGroup(principal, List.of()));

        FamilyEligibilityResponseDto mappedResponse = FamilyEligibilityResponseDto.builder()
                .principal(MemberViewDto.builder().id(100L).build())
                .dependents(List.of())
                .totalFamilyMembers(1)
                .build();
        when(mapper.toFamilyEligibilityResponse(eq(principal), eq(List.of()), any()))
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
    void delegatesFamilyResolutionAndEvaluationToTheSharedOrchestrator() {
        setUpPrincipal();
        Map<Long, EligibilityResult> results = Map.of(100L, EligibilityResult.eligible("req-1", null, 5L, 3));
        when(familyEligibilityService.evaluateFamily(principal, List.of(), null)).thenReturn(results);

        service.checkFamilyEligibility("WAHA-2026-000100", null);

        verify(familyEligibilityService).resolveFamily(principal);
        verify(familyEligibilityService).evaluateFamily(principal, List.of(), null);
        verify(mapper).toFamilyEligibilityResponse(principal, List.of(), results);
    }

    @Test
    void threadsTheCallerSuppliedServiceDateThroughToTheOrchestrator() {
        setUpPrincipal();
        LocalDate backdated = LocalDate.of(2026, 1, 15);
        when(familyEligibilityService.evaluateFamily(any(), any(), eq(backdated)))
                .thenReturn(Map.of(100L, EligibilityResult.eligible("req-2", null, 5L, 3)));

        service.checkFamilyEligibility("WAHA-2026-000100", backdated);

        verify(familyEligibilityService).evaluateFamily(principal, List.of(), backdated);
    }

    @Test
    void financialLookupFailureIsSurfacedExplicitlyNotSwallowed() {
        setUpPrincipal();
        when(familyEligibilityService.evaluateFamily(any(), any(), any()))
                .thenReturn(Map.of(100L, EligibilityResult.eligible("req-3", null, 5L, 3)));
        when(financialSummaryService.getFinancialSummaries(any())).thenThrow(new RuntimeException("db timeout"));

        FamilyEligibilityResponseDto response = service.checkFamilyEligibility("WAHA-2026-000100", null);

        assertThat(response.getFinancialDataAvailable()).isFalse();
        assertThat(response.getFinancialDataError()).isNotBlank();
    }

    @Test
    void financialLookupSuccessLeavesDataAvailableTrue() {
        setUpPrincipal();
        when(familyEligibilityService.evaluateFamily(any(), any(), any()))
                .thenReturn(Map.of(100L, EligibilityResult.eligible("req-4", null, 5L, 3)));

        FamilyEligibilityResponseDto response = service.checkFamilyEligibility("WAHA-2026-000100", null);

        assertThat(response.getFinancialDataAvailable()).isTrue();
        assertThat(response.getFinancialDataError()).isNull();
    }

    @Test
    void barcodeOnlyOverloadDefaultsServiceDateToNull() {
        setUpPrincipal();
        when(familyEligibilityService.evaluateFamily(any(), any(), any()))
                .thenReturn(Map.of(100L, EligibilityResult.eligible("req-5", null, 5L, 3)));

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        service.checkEligibility("WAHA-2026-000100");

        verify(familyEligibilityService).evaluateFamily(eq(principal), eq(List.of()), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isNull();
    }
}
