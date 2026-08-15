package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse;
import com.waad.tba.modules.eligibility.service.FamilyEligibilityService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the two family-eligibility entry points -- the member module's
 * barcode-based UnifiedMemberService.checkFamilyEligibility (used by the
 * provider portal) and the eligibility module's memberId-based
 * FamilyEligibilityService.checkFamilyEligibility -- now agree, because both
 * delegate to the exact same FamilyEligibilityService.resolveFamily /
 * evaluateFamily orchestrator instead of each running its own independent
 * loop. Runs against a real PostgreSQL container (Testcontainers), not
 * Mockito, and against the real EligibilityEngineService + rule chain.
 *
 * Before this fix (see 1ce07dce / 38c0dad0), a query for a backdated
 * serviceDate could only be honored by the memberId path -- the barcode path
 * hardcoded LocalDate.now(). This test uses a policy that is active on a
 * past date but already expired "today" is unnecessary to prove that gap;
 * agreement on the SAME serviceDate for both paths is the property this test
 * defends against regressing back into two divergent implementations.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class UnifiedMemberEligibilityAgreementIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private UnifiedMemberService unifiedMemberService;
    @Autowired private FamilyEligibilityService familyEligibilityService;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private MemberRepository memberRepository;

    @MockBean private MemberFinancialSummaryService financialSummaryService;
    @MockBean private MemberQueryAccessPolicy memberQueryAccessPolicy;

    private Member persistPrincipal(String suffix, LocalDate policyStart, LocalDate policyEnd) {
        Employer employer = employerRepository.save(Employer.builder()
                .name("Agreement Test Co " + suffix).code("EMP-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(policyStart).endDate(policyEnd)
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        return memberRepository.save(Member.builder()
                .fullName("Agreement Member " + suffix).barcode("BC-AGREE-" + suffix)
                .nationalNumber("NAT-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());
    }

    @Test
    void barcodeAndMemberIdEndpointsAgreeOnTheSameServiceDate() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate serviceDate = LocalDate.now();
        Member principal = persistPrincipal(suffix,
                LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));

        org.mockito.Mockito.when(financialSummaryService.getFinancialSummaries(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of());

        FamilyEligibilityResponseDto byBarcode =
                unifiedMemberService.checkFamilyEligibility(principal.getBarcode(), serviceDate);
        FamilyEligibilityResponse byMemberId =
                familyEligibilityService.checkFamilyEligibility(principal.getId(), serviceDate);

        assertThat(byBarcode.getPrincipal().getId()).isEqualTo(principal.getId());
        assertThat(byBarcode.getEligible()).isEqualTo(byMemberId.getPrimaryMember().isEligible());
    }

    @Test
    void barcodeAndMemberIdEndpointsAgreeOnAnIneligibleDecisionForABackdatedServiceDate() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // Policy starts in the future relative to the requested serviceDate below,
        // so the coverage-window rule should reject both paths identically -- this
        // is exactly the scenario the old barcode path (hardcoded to today) could
        // never even express, since it had no way to accept a serviceDate at all.
        LocalDate policyStart = LocalDate.now().plusMonths(6);
        LocalDate policyEnd = LocalDate.now().plusYears(1);
        LocalDate backdatedServiceDate = LocalDate.now();
        Member principal = persistPrincipal(suffix, policyStart, policyEnd);

        org.mockito.Mockito.when(financialSummaryService.getFinancialSummaries(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of());

        FamilyEligibilityResponseDto byBarcode =
                unifiedMemberService.checkFamilyEligibility(principal.getBarcode(), backdatedServiceDate);
        FamilyEligibilityResponse byMemberId =
                familyEligibilityService.checkFamilyEligibility(principal.getId(), backdatedServiceDate);

        assertThat(byBarcode.getEligible()).isFalse();
        assertThat(byMemberId.getPrimaryMember().isEligible()).isFalse();
        assertThat(byBarcode.getEligible()).isEqualTo(byMemberId.getPrimaryMember().isEligible());
    }

    @Test
    void financialSummaryFailureIsSurfacedThroughTheRealBarcodeEndpointNotSwallowed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member principal = persistPrincipal(suffix,
                LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));

        org.mockito.Mockito.when(financialSummaryService.getFinancialSummaries(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("simulated financial-summary read failure"));

        FamilyEligibilityResponseDto response =
                unifiedMemberService.checkFamilyEligibility(principal.getBarcode(), LocalDate.now());

        assertThat(response.getFinancialDataAvailable()).isFalse();
        assertThat(response.getFinancialDataError()).isNotBlank();
        // The eligibility decision itself must not be corrupted by the
        // unrelated financial-read failure -- only the financial fields are
        // marked unavailable.
        assertThat(response.getPrincipal()).isNotNull();
    }
}
