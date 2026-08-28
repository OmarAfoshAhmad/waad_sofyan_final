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
        assertThat(view.remainingLimit()).isNull();
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
        when(benefitPolicyCoverageService.getRemainingCoverage(policy, 77L, asOfDate))
                .thenReturn(new BigDecimal("750.00"));

        ProviderPortalService.AnnualLimitView view =
                service.calculateAnnualLimitView(member, policy, asOfDate);

        assertThat(view.annualLimit()).isEqualByComparingTo("1000.00");
        assertThat(view.usedAmount()).isEqualByComparingTo("250.00");
        assertThat(view.remainingLimit()).isEqualByComparingTo("750.00");
        assertThat(view.usagePercentage()).isEqualTo(25.0);
    }
}
