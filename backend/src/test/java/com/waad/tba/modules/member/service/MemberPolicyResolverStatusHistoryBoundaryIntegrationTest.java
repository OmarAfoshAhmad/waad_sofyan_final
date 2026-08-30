package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The exact boundary matrix demanded when closing the "a suspended policy
 * must not erase historical claims" defect (see
 * BenefitPolicyLifecycleIntegrationGateTest for the full chained scenario;
 * this covers the half-open [validFrom, validTo) edges directly, which a
 * single end-to-end chain cannot exercise precisely).
 *
 * Every case here writes benefit_policy_status_history rows directly,
 * bypassing BenefitPolicyService's lifecycle methods, so the exact
 * boundary dates are under the test's control rather than "whatever day
 * the test happens to run on".
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberPolicyResolverStatusHistoryBoundaryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private MemberRepository members;
    @Autowired private EmployerRepository employers;
    @Autowired private com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository policies;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employerId;
    private long policyId;
    private Member member;

    @BeforeEach
    void seedAPolicyAndAnAlwaysCoveredMember() {
        String s = suffix();
        Employer employer = employers.save(Employer.builder()
                .name("جهة حدود الحالة " + s).code("PBOUND-" + s).active(true).build());
        employerId = employer.getId();

        var policy = policies.save(com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.builder()
                .name("وثيقة حدود الحالة " + s).policyCode("PBOUND-POL-" + s).employer(employer)
                .startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1))
                .annualLimit(new BigDecimal("10000")).defaultCoveragePercent(80)
                .status(com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus.DRAFT)
                .active(true).build());
        policyId = policy.getId();

        member = members.save(Member.builder()
                .fullName("عضو حدود الحالة " + s).barcode("PBOUND-M-" + s)
                .nationalNumber("PBOUND-NAT-" + s).employer(employer).benefitPolicy(policy)
                .active(true).build());
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date)"
                + " VALUES (?, ?, ?)", member.getId(), policyId, LocalDate.now().minusYears(1));
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date,"
                + " assignment_reason, assignment_source) VALUES (?, ?, ?, 'تجهيز اختبار', 'MANUAL')",
                member.getId(), employerId, LocalDate.now().minusYears(1));
    }

    private void history(String status, LocalDate validFrom, LocalDate validTo) {
        jdbc.update("INSERT INTO benefit_policy_status_history (policy_id, status, valid_from, valid_to)"
                + " VALUES (?, ?, ?, ?)", policyId, status, validFrom, validTo);
    }

    @Test
    @DisplayName("a service date inside a SUSPENDED interval does not resolve")
    void serviceDuringSuspendedWindowDoesNotResolve() {
        LocalDate active1Start = LocalDate.now().minusDays(60);
        LocalDate suspendDay = LocalDate.now().minusDays(30);
        LocalDate reactivateDay = LocalDate.now().minusDays(10);
        history("ACTIVE", active1Start, suspendDay);
        history("SUSPENDED", suspendDay, reactivateDay);
        history("ACTIVE", reactivateDay, null);

        assertThat(policyResolver.resolveFor(member, suspendDay.plusDays(5)))
                .as("a service date inside the SUSPENDED window must not resolve")
                .isEmpty();
    }

    @Test
    @DisplayName("reactivating after a suspension resolves again for a new service date")
    void reactivationResolvesAgainForANewServiceDate() {
        LocalDate active1Start = LocalDate.now().minusDays(60);
        LocalDate suspendDay = LocalDate.now().minusDays(30);
        LocalDate reactivateDay = LocalDate.now().minusDays(10);
        history("ACTIVE", active1Start, suspendDay);
        history("SUSPENDED", suspendDay, reactivateDay);
        history("ACTIVE", reactivateDay, null);

        var resolved = policyResolver.resolveFor(member, reactivateDay.plusDays(2));
        assertThat(resolved).as("a service date after reactivation resolves again").isPresent();
        assertThat(resolved.get().getId()).isEqualTo(policyId);
    }

    @Test
    @DisplayName("half-open interval: the day BEFORE a status change still belongs to the old status")
    void theDayBeforeATransitionBelongsToTheOldStatus() {
        LocalDate activeStart = LocalDate.now().minusDays(60);
        LocalDate suspendDay = LocalDate.now().minusDays(30);
        history("ACTIVE", activeStart, suspendDay);
        history("SUSPENDED", suspendDay, null);

        assertThat(policyResolver.resolveFor(member, suspendDay.minusDays(1)))
                .as("[validFrom, validTo): the day right before validTo is still the OLD (ACTIVE) status")
                .isPresent();
    }

    @Test
    @DisplayName("half-open interval: the transition day itself belongs to the NEW status")
    void theTransitionDayItselfBelongsToTheNewStatus() {
        LocalDate activeStart = LocalDate.now().minusDays(60);
        LocalDate suspendDay = LocalDate.now().minusDays(30);
        history("ACTIVE", activeStart, suspendDay);
        history("SUSPENDED", suspendDay, null);

        assertThat(policyResolver.resolveFor(member, suspendDay))
                .as("[validFrom, validTo): validTo is EXCLUDED from the old interval -- "
                        + "the transition day belongs to the new (SUSPENDED) status, so this must not resolve")
                .isEmpty();
    }

    @Test
    @DisplayName("half-open interval: the day a status starts (validFrom) is included in it")
    void theValidFromDayIsIncludedInTheNewInterval() {
        LocalDate activeStart = LocalDate.now().minusDays(60);
        history("ACTIVE", activeStart, null);

        assertThat(policyResolver.resolveFor(member, activeStart))
                .as("[validFrom, validTo): validFrom is INCLUDED")
                .isPresent();
    }

    @Test
    @DisplayName("changing the CURRENT status does not change what an earlier resolved date reports")
    void changingCurrentStatusDoesNotChangeAnEarlierResolvedDate() {
        LocalDate activeStart = LocalDate.now().minusYears(1);
        LocalDate serviceDate = LocalDate.now().minusMonths(2);
        history("ACTIVE", activeStart, null);

        var before = policyResolver.resolveFor(member, serviceDate);
        assertThat(before).isPresent();

        // The current status changes TODAY -- a separate, later interval.
        jdbc.update("UPDATE benefit_policy_status_history SET valid_to = ? WHERE policy_id = ? AND valid_to IS NULL",
                LocalDate.now(), policyId);
        history("SUSPENDED", LocalDate.now(), null);
        jdbc.update("UPDATE benefit_policies SET status = 'SUSPENDED' WHERE id = ?", policyId);

        var after = policyResolver.resolveFor(member, serviceDate);
        assertThat(after)
                .as("re-resolving the SAME past service date after today's status change must report the SAME policy")
                .isPresent();
        assertThat(after.get().getId()).isEqualTo(before.get().getId());
    }
}
