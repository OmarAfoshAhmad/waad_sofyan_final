package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimHistoricalContextStatus;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;

/**
 * C1: the guard against a future adjacent-module test repeating the same
 * mistake (a {@code Claim.builder()} row left with
 * {@code historicalContextStatus = RESOLVED} -- the entity's own default --
 * but no real policy/employer assignment behind it). This does not re-check
 * the DB constraint itself (V219's own migration test already does that
 * exhaustively); it proves the SHARED FIXTURE every adjacent test is meant to
 * reuse actually produces a context V219 accepts, end to end, including a
 * real {@code claims} insert -- so trusting {@link ResolvedClaimTestFixture}
 * is trusting something itself verified, not assumed.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ResolvedClaimTestFixtureTest extends PostgresIntegrationTestBase {

    @Autowired private EmployerRepository employers;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private MemberRepository members;
    @Autowired private ProviderRepository providers;
    @Autowired private VisitRepository visits;
    @Autowired private ClaimRepository claims;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void resolveProducesAContextARealResolvedClaimCanBePersistedWith() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employer employer = employers.save(Employer.builder()
                .name("Fixture Guard Co " + suffix).code("FXG-" + suffix).active(true).build());
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("Fixture Guard Policy " + suffix).policyCode("FXGPOL-" + suffix)
                .employer(employer).annualLimit(new BigDecimal("50000.00")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        Member member = members.save(Member.builder().fullName("Fixture Guard Member " + suffix)
                .barcode("FXG-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        initializeTemporalAssignments(member);
        var resolved = ResolvedClaimTestFixture.resolve(jdbc, member.getId());

        assertThat(resolved.policyId()).isNotNull();
        assertThat(resolved.policyAssignmentId()).isNotNull();
        assertThat(resolved.employerAssignmentId()).isNotNull();
        assertThat(resolved.policyId()).isEqualTo(policy.getId());

        Provider provider = providers.save(Provider.builder()
                .name("Fixture Guard Provider " + suffix).licenseNumber("FXG-" + suffix)
                .providerType(ProviderType.CLINIC).active(true).build());
        Visit visit = visits.save(Visit.builder().member(member).providerId(provider.getId())
                .visitDate(LocalDate.now()).status(VisitStatus.REGISTERED).build());

        Claim claim = Claim.builder().member(member).visit(visit).providerId(provider.getId())
                .serviceDate(LocalDate.now())
                .requestedAmount(new BigDecimal("10.00")).approvedAmount(new BigDecimal("10.00"))
                .status(com.waad.tba.modules.claim.entity.ClaimStatus.DRAFT)
                .historicalContextStatus(ClaimHistoricalContextStatus.RESOLVED)
                .policyId(resolved.policyId())
                .policyAssignmentId(resolved.policyAssignmentId())
                .employerAssignmentId(resolved.employerAssignmentId())
                .build();
        ClaimLine line = ClaimLine.builder().claim(claim).serviceCode("FXG-SVC")
                .serviceName("Fixture Guard Service").quantity(1)
                .unitPrice(new BigDecimal("10.00")).totalPrice(new BigDecimal("10.00"))
                .requestedTotal(new BigDecimal("10.00")).approvedAmount(new BigDecimal("10.00"))
                .companyShare(new BigDecimal("10.00")).patientShare(BigDecimal.ZERO).build();
        claim.setLines(List.of(line));

        // If chk_claims_historical_context_consistency ever rejects this, the
        // shared fixture itself is broken -- every adjacent-module test that
        // trusts it needs to know that immediately, not discover it through
        // an unrelated test's stack trace.
        Claim persisted = claims.saveAndFlush(claim);
        assertThat(persisted.getId()).isNotNull();
    }
}
