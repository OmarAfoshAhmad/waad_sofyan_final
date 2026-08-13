package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.eligibility.service.EligibilityEngineService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.repository.MemberPolicyAssignmentRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Real-Postgres proof that "which policy applied to this member" is answered
 * BY SERVICE DATE, from one shared resolver, and that resolving never writes.
 *
 * Before MemberPolicyResolver, the eligibility engine read
 * member.getBenefitPolicy() with no reference to serviceDate at all, and the
 * coverage validator read the same pointer and silently PERSISTED a
 * date-derived lookup back onto the member when it was null.
 */
@SpringBootTest(classes = com.waad.tba.TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberPolicyResolverIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberPolicyResolver resolver;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberPolicyAssignmentRepository assignmentRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository policyRepository;
    @Autowired private EligibilityEngineService eligibilityService;
    @Autowired private BenefitPolicyCoverageService coverageService;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Employer newEmployer(String s) {
        return employerRepository.save(Employer.builder()
                .name("Policy Co " + s).code("PC-" + s).active(true).build());
    }

    private BenefitPolicy newPolicy(Employer employer, String s, BigDecimal annualLimit,
            LocalDate start, LocalDate end) {
        return policyRepository.save(BenefitPolicy.builder()
                .name("Plan " + s).policyCode("POL-PC-" + s).employer(employer)
                .annualLimit(annualLimit).defaultCoveragePercent(80)
                .startDate(start).endDate(end)
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
    }

    private Member newMember(Employer employer, BenefitPolicy policy, String s) {
        return memberRepository.save(Member.builder()
                .fullName("Policy Member " + s).employer(employer).benefitPolicy(policy)
                .cardNumber("PCARD" + s).barcode("PCARD" + s)
                .status(Member.MemberStatus.ACTIVE).active(true).build());
    }

    /** A backdated question is answered with the policy of that date, not today's. */
    @Test
    void resolvesThePolicyInForceOnTheServiceDateNotTheCurrentPointer() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy oldPolicy = newPolicy(employer, s + "-old", new BigDecimal("10000"),
                LocalDate.now().minusYears(2), LocalDate.now().minusMonths(6));
        BenefitPolicy newPolicy = newPolicy(employer, s + "-new", new BigDecimal("90000"),
                LocalDate.now().minusMonths(6), LocalDate.now().plusYears(1));
        Member member = newMember(employer, oldPolicy, s);

        LocalDate switchDate = LocalDate.now().minusMonths(6);
        resolver.assignPolicy(member, oldPolicy, LocalDate.now().minusYears(2),
                "التعيين الأول", PolicyAssignmentSource.MANUAL, 1L);
        resolver.assignPolicy(member, newPolicy, switchDate,
                "انتقال إلى وثيقة جديدة", PolicyAssignmentSource.MANUAL, 1L);
        memberRepository.saveAndFlush(member);

        // A date inside the OLD period resolves to the old policy, even though
        // the member's current pointer is now the new one.
        assertThat(resolver.resolveFor(member, LocalDate.now().minusYears(1)).orElseThrow().getId())
                .isEqualTo(oldPolicy.getId());
        // The switch day itself belongs to the NEW policy -- ranges are
        // half-open [start, end), so periods meet without overlapping.
        assertThat(resolver.resolveFor(member, switchDate).orElseThrow().getId())
                .isEqualTo(newPolicy.getId());
        assertThat(resolver.resolveFor(member, LocalDate.now()).orElseThrow().getId())
                .isEqualTo(newPolicy.getId());
    }

    /** A date before any assignment is "no coverage", never today's policy. */
    @Test
    void aDateBeforeAnyAssignmentResolvesToNothingRatherThanFabricatingCoverage() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s, new BigDecimal("10000"),
                LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1));
        Member member = newMember(employer, policy, s);

        resolver.assignPolicy(member, policy, LocalDate.now().minusMonths(1),
                "تعيين", PolicyAssignmentSource.MANUAL, 1L);
        memberRepository.saveAndFlush(member);

        assertThat(resolver.resolveFor(member, LocalDate.now().minusYears(3)))
                .as("member had no policy three years before they were assigned one")
                .isEmpty();
    }

    /** Resolution is pure -- it must never rewrite the member's stored policy. */
    @Test
    void resolvingDoesNotWriteAnythingBackToTheMember() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy assigned = newPolicy(employer, s + "-a", new BigDecimal("10000"),
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        Member member = newMember(employer, assigned, s);
        resolver.assignPolicy(member, assigned, LocalDate.now().minusYears(1),
                "تعيين", PolicyAssignmentSource.MANUAL, 1L);
        memberRepository.saveAndFlush(member);
        Long pointerBefore = memberRepository.findById(member.getId()).orElseThrow()
                .getBenefitPolicy().getId();

        // Ask about a date with no coverage -- the old code would have looked
        // one up and persisted it.
        resolver.resolveFor(member, LocalDate.now().minusYears(5));
        coverageServiceSafeValidate(member, LocalDate.now().minusYears(5));

        Long pointerAfter = memberRepository.findById(member.getId()).orElseThrow()
                .getBenefitPolicy().getId();
        assertThat(pointerAfter).isEqualTo(pointerBefore);
    }

    private void coverageServiceSafeValidate(Member member, LocalDate date) {
        try {
            coverageService.validateMemberHasActivePolicy(member, date);
        } catch (BusinessRuleException expected) {
            // Expected: no policy on that date. The point of the test is that
            // it did not silently repoint the member instead.
        }
    }

    /** Postgres itself refuses two assignments covering the same day. */
    @Test
    void overlappingAssignmentsAreRejectedByTheDatabase() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy a = newPolicy(employer, s + "-a", new BigDecimal("10000"),
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        BenefitPolicy b = newPolicy(employer, s + "-b", new BigDecimal("20000"),
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        Member member = newMember(employer, a, s);
        resolver.assignPolicy(member, a, LocalDate.now().minusMonths(6),
                "تعيين", PolicyAssignmentSource.MANUAL, 1L);
        memberRepository.saveAndFlush(member);

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO member_policy_assignments "
                            + "(member_id, policy_id, assignment_start_date, assignment_end_date, assignment_source) "
                            + "VALUES (?, ?, ?, NULL, 'MANUAL')")) {
                ps.setLong(1, member.getId());
                ps.setLong(2, b.getId());
                ps.setObject(3, LocalDate.now().minusMonths(3));
                assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("uk_member_policy_assignment_no_overlap");
            }
        }
    }

    /** Assignment rows are append-only: never deleted, never re-pointed. */
    @Test
    void assignmentRowsCannotBeDeletedOrRepointed() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy a = newPolicy(employer, s, new BigDecimal("10000"),
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        Member member = newMember(employer, a, s);
        var assignment = resolver.assignPolicy(member, a, LocalDate.now().minusMonths(6),
                "تعيين", PolicyAssignmentSource.MANUAL, 1L);
        memberRepository.saveAndFlush(member);

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM member_policy_assignments WHERE id = ?")) {
                del.setLong(1, assignment.getId());
                assertThatThrownBy(del::executeUpdate).hasMessageContaining("append-only");
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE member_policy_assignments SET assignment_start_date = ? WHERE id = ?")) {
                upd.setObject(1, LocalDate.now().minusYears(5));
                upd.setLong(2, assignment.getId());
                assertThatThrownBy(upd::executeUpdate)
                        .hasMessageContaining("only assignment_end_date may be updated");
            }
        }
    }

    /**
     * The whole point: eligibility and claims now give the SAME answer for the
     * same member and date, because they share one resolver. Previously the
     * eligibility engine ignored serviceDate entirely.
     */
    @Test
    void eligibilityAndCoverageResolveTheSamePolicyForTheSameServiceDate() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy oldPolicy = newPolicy(employer, s + "-old", new BigDecimal("10000"),
                LocalDate.now().minusYears(2), LocalDate.now().minusMonths(3));
        BenefitPolicy currentPolicy = newPolicy(employer, s + "-cur", new BigDecimal("50000"),
                LocalDate.now().minusMonths(3), LocalDate.now().plusYears(1));
        Member member = newMember(employer, oldPolicy, s);
        resolver.assignPolicy(member, oldPolicy, LocalDate.now().minusYears(2),
                "الأولى", PolicyAssignmentSource.MANUAL, 1L);
        resolver.assignPolicy(member, currentPolicy, LocalDate.now().minusMonths(3),
                "الثانية", PolicyAssignmentSource.MANUAL, 1L);
        memberRepository.saveAndFlush(member);

        LocalDate backdated = LocalDate.now().minusMonths(12);

        var eligibilityResult = eligibilityService.checkEligibility(EligibilityCheckRequest.builder()
                .memberId(member.getId()).serviceDate(backdated).build());

        assertThat(eligibilityResult.getSnapshot()).isNotNull();
        assertThat(eligibilityResult.getSnapshot().getPolicyNumber())
                .as("eligibility must report the policy that applied on the SERVICE DATE")
                .isEqualTo(oldPolicy.getPolicyCode());

        assertThat(resolver.resolveFor(member, backdated).orElseThrow().getPolicyCode())
                .isEqualTo(eligibilityResult.getSnapshot().getPolicyNumber());
    }
}
