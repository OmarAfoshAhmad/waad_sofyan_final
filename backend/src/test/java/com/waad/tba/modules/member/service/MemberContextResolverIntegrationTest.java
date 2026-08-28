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
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.repository.MemberEmployerAssignmentRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = com.waad.tba.TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberContextResolverIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberContextResolver contextResolver;
    @Autowired private MemberEmployerResolver employerResolver;
    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository policyRepository;
    @Autowired private MemberEmployerAssignmentRepository employerAssignmentRepository;

    @Test
    void resolvesEmployerAndPolicyFromTheSameHistoricalDateNotCurrentPointers() {
        String s = suffix();
        Employer oldEmployer = employer("Old " + s, "OLD-" + s);
        Employer newEmployer = employer("New " + s, "NEW-" + s);
        LocalDate switchDate = LocalDate.now().minusMonths(6);
        BenefitPolicy oldPolicy = policy(oldEmployer, "OLD-P-" + s,
                LocalDate.now().minusYears(3), switchDate.minusDays(1));
        BenefitPolicy newPolicy = policy(newEmployer, "NEW-P-" + s,
                switchDate, LocalDate.now().plusYears(2));
        Member member = member(oldEmployer, oldPolicy, s);

        employerResolver.assignEmployer(member, oldEmployer, LocalDate.now().minusYears(3),
                "old employer", EmployerAssignmentSource.MANUAL, 1L);
        member = memberRepository.findById(member.getId()).orElseThrow();
        policyResolver.assignPolicy(member, oldPolicy, LocalDate.now().minusYears(3),
                "old policy", PolicyAssignmentSource.MANUAL, 1L);
        member = memberRepository.findById(member.getId()).orElseThrow();
        employerResolver.assignEmployer(member, newEmployer, switchDate,
                "employer transfer", EmployerAssignmentSource.MANUAL, 1L);
        member = memberRepository.findById(member.getId()).orElseThrow();
        policyResolver.assignPolicy(member, newPolicy, switchDate,
                "new policy", PolicyAssignmentSource.MANUAL, 1L);

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getEmployer().getId()).isEqualTo(newEmployer.getId());
        assertThat(reloaded.getBenefitPolicy().getId()).isEqualTo(newPolicy.getId());

        MemberDatedContext historical = contextResolver.resolveForOrFail(
                reloaded, LocalDate.now().minusYears(1));
        assertThat(historical.employer().getId()).isEqualTo(oldEmployer.getId());
        assertThat(historical.policy().getId()).isEqualTo(oldPolicy.getId());

        MemberDatedContext current = contextResolver.resolveForOrFail(reloaded, switchDate);
        assertThat(current.employer().getId()).isEqualTo(newEmployer.getId());
        assertThat(current.policy().getId()).isEqualTo(newPolicy.getId());
    }

    @Test
    void missingDateAndMissingEmployerHistoryBothFailClosed() {
        String s = suffix();
        Employer employer = employer("Employer " + s, "EMP-" + s);
        BenefitPolicy policy = policy(employer, "P-" + s,
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        Member member = member(employer, policy, s);
        policyResolver.assignPolicy(member, policy, LocalDate.now().minusYears(1),
                "policy only", PolicyAssignmentSource.MANUAL, 1L);

        assertThatThrownBy(() -> contextResolver.resolveForOrFail(member, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("تاريخ الخدمة إلزامي");
        assertThatThrownBy(() -> contextResolver.resolveForOrFail(member, LocalDate.now()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("تعيين جهة عمل");
    }

    @Test
    void postgresRejectsOverlapAndMakesEmployerHistoryAppendOnly() throws Exception {
        String s = suffix();
        Employer employer = employer("Employer " + s, "EMP-" + s);
        Member member = member(employer, null, s);
        var assignment = employerResolver.assignEmployer(member, employer, LocalDate.now().minusYears(1),
                "initial", EmployerAssignmentSource.MANUAL, 1L);

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement overlap = connection.prepareStatement(
                    "INSERT INTO member_employer_assignments "
                            + "(member_id, employer_id, assignment_start_date, assignment_reason, "
                            + "assignment_source) VALUES (?, ?, ?, 'overlap', 'MANUAL')")) {
                overlap.setLong(1, member.getId());
                overlap.setLong(2, employer.getId());
                overlap.setObject(3, LocalDate.now().minusMonths(1));
                assertThatThrownBy(overlap::executeUpdate)
                        .hasMessageContaining("uk_member_employer_assignment_no_overlap");
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM member_employer_assignments WHERE id = ?")) {
                delete.setLong(1, assignment.getId());
                assertThatThrownBy(delete::executeUpdate).hasMessageContaining("append-only");
            }
            try (PreparedStatement rewrite = connection.prepareStatement(
                    "UPDATE member_employer_assignments SET employer_id = employer_id + 1 WHERE id = ?")) {
                rewrite.setLong(1, assignment.getId());
                assertThatThrownBy(rewrite::executeUpdate)
                        .hasMessageContaining("only assignment_end_date may be updated");
            }
        }

        assertThat(employerAssignmentRepository.findByMemberIdOrderByAssignmentStartDateDesc(member.getId()))
                .hasSize(1);
    }

    @Test
    void concurrentEmployerTransfersProduceOneWinnerAndOneOpenAssignment() throws Exception {
        String s = suffix();
        Employer original = employer("Original " + s, "ORIG-" + s);
        Employer candidateA = employer("Candidate A " + s, "A-" + s);
        Employer candidateB = employer("Candidate B " + s, "B-" + s);
        Member member = member(original, null, s);
        employerResolver.assignEmployer(member, original, LocalDate.now().minusYears(1),
                "original employer", EmployerAssignmentSource.MANUAL, 1L);

        Long memberId = member.getId();
        LocalDate effectiveFrom = LocalDate.now();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);

        java.util.concurrent.Callable<Boolean> transferA = () -> {
            gate.await();
            try {
                Member fresh = memberRepository.findById(memberId).orElseThrow();
                employerResolver.assignEmployer(fresh, candidateA, effectiveFrom,
                        "concurrent transfer A", EmployerAssignmentSource.MANUAL, 1L);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        };
        java.util.concurrent.Callable<Boolean> transferB = () -> {
            gate.await();
            try {
                Member fresh = memberRepository.findById(memberId).orElseThrow();
                employerResolver.assignEmployer(fresh, candidateB, effectiveFrom,
                        "concurrent transfer B", EmployerAssignmentSource.MANUAL, 1L);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        };

        var first = pool.submit(transferA);
        var second = pool.submit(transferB);
        gate.countDown();
        boolean firstWon = first.get(60, java.util.concurrent.TimeUnit.SECONDS);
        boolean secondWon = second.get(60, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(firstWon ^ secondWon).as("exactly one concurrent transfer may win").isTrue();
        var assignments = employerAssignmentRepository
                .findByMemberIdOrderByAssignmentStartDateDesc(memberId);
        assertThat(assignments).hasSize(2);
        assertThat(assignments.stream().filter(a -> a.getAssignmentEndDate() == null)).hasSize(1);
        assertThat(assignments.stream().filter(a -> a.getAssignmentEndDate() != null)).hasSize(1);
        Member persisted = memberRepository.findById(memberId).orElseThrow();
        assertThat(employerResolver.resolveForOrFail(persisted, effectiveFrom).getId())
                .isIn(candidateA.getId(), candidateB.getId());
    }

    private Employer employer(String name, String code) {
        return employerRepository.save(Employer.builder().name(name).code(code).active(true).build());
    }

    private BenefitPolicy policy(Employer employer, String code, LocalDate start, LocalDate end) {
        return policyRepository.save(BenefitPolicy.builder()
                .name(code).policyCode(code).employer(employer)
                .annualLimit(new BigDecimal("60000")).defaultCoveragePercent(80)
                .startDate(start).endDate(end).status(BenefitPolicyStatus.ACTIVE).active(true).build());
    }

    private Member member(Employer employer, BenefitPolicy policy, String suffix) {
        return memberRepository.save(Member.builder()
                .fullName("Temporal Member " + suffix).employer(employer).benefitPolicy(policy)
                .cardNumber("T-" + suffix).barcode("T-" + suffix)
                .status(policy == null ? Member.MemberStatus.TERMINATED : Member.MemberStatus.ACTIVE)
                .active(policy != null).build());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
