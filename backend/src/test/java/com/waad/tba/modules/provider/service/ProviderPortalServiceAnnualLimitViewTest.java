package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.member.service.UnifiedMemberService;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.security.ProviderContextGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderPortalServiceAnnualLimitViewTest {

    @Mock
    private UnifiedMemberService unifiedMemberService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BenefitPolicyCoverageService benefitPolicyCoverageService;
    @Mock
    private BenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private VisitRepository visitRepository;
    @Mock
    private ProviderContextGuard providerContextGuard;
    @Mock
    private PreAuthorizationRepository preAuthorizationRepository;
    @Mock
    private MemberPolicyResolver memberPolicyResolver;

    @InjectMocks
    private ProviderPortalService service;

    @Test
    void zeroAnnualLimitMeansNoGeneralCeilingNotZeroRemaining() {
        Member member = mock(Member.class);
        BenefitPolicy policy = mock(BenefitPolicy.class);
        when(policy.getAnnualLimit()).thenReturn(BigDecimal.ZERO);

        ProviderPortalService.AnnualLimitView view =
                service.calculateAnnualLimitView(member, policy, LocalDate.of(2026, 8, 20));

        assertThat(view.annualLimit()).isNull();
        assertThat(view.usedAmount()).isNull();
        assertThat(view.reservableAvailable()).isNull();
        assertThat(view.reservedAmount()).isNull();
        assertThat(view.actualRemaining()).isNull();
        assertThat(view.usagePercentage()).isNull();
        verifyNoInteractions(benefitPolicyCoverageService);
    }

    @Test
    void positiveAnnualLimitUsesPolicyFilteredCoverageReader() {
        Member member = mock(Member.class);
        BenefitPolicy policy = mock(BenefitPolicy.class);
        LocalDate asOfDate = LocalDate.of(2026, 8, 20);
        when(member.getId()).thenReturn(77L);
        when(policy.getAnnualLimit()).thenReturn(new BigDecimal("1000.00"));
        when(benefitPolicyCoverageService.readGeneralCeiling(policy, 77L, asOfDate))
                .thenReturn(com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading.found(
                        new BigDecimal("1000.00"), new BigDecimal("250.00"), new BigDecimal("100.00")));

        ProviderPortalService.AnnualLimitView view =
                service.calculateAnnualLimitView(member, policy, asOfDate);

        assertThat(view.annualLimit()).isEqualByComparingTo("1000.00");
        assertThat(view.usedAmount())
                .as("consumption is read, not inferred from what is left")
                .isEqualByComparingTo("250.00");
        assertThat(view.reservedAmount()).isEqualByComparingTo("100.00");
        assertThat(view.reservableAvailable())
                .as("the provider is about to submit, so the figure that reaches them "
                        + "is what may still be committed")
                .isEqualByComparingTo("650.00");
        assertThat(view.actualRemaining())
                .as("and the accounting figure travels beside it")
                .isEqualByComparingTo("750.00");
        assertThat(view.usagePercentage()).isEqualTo(25.0);
    }

    @Test
    void anOverspentMemberIsNotShownAsExactlySpent() {
        Member member = mock(Member.class);
        BenefitPolicy policy = mock(BenefitPolicy.class);
        LocalDate asOfDate = LocalDate.of(2026, 8, 20);
        when(member.getId()).thenReturn(78L);
        when(policy.getAnnualLimit()).thenReturn(new BigDecimal("1000.00"));
        when(benefitPolicyCoverageService.readGeneralCeiling(policy, 78L, asOfDate))
                .thenReturn(com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading.found(
                        new BigDecimal("1000.00"), new BigDecimal("1200.00"), BigDecimal.ZERO));

        ProviderPortalService.AnnualLimitView view =
                service.calculateAnnualLimitView(member, policy, asOfDate);

        assertThat(view.actualRemaining()).isEqualByComparingTo("-200.00");
        assertThat(view.usedAmount()).isEqualByComparingTo("1200.00");
    }
}
