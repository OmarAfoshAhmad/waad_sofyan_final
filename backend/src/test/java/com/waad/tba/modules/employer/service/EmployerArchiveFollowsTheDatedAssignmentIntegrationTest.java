package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Whether an employer may be archived is a DATED question -- does anyone
 * belong to it now -- and it must be answered from
 * member_employer_assignments, not from the members.employer_id pointer.
 *
 * The first two cases below only prove the guard behaves sensibly. The last
 * two are the ones that prove WHICH SOURCE it read, and they do it the only
 * way that can: by making the pointer and the assignment disagree on purpose
 * and seeing which one the decision follows.
 *
 * That disagreement is not expected in production. MemberEmployerResolver
 * writes both together, so they agree -- which is exactly why reading the
 * pointer looked harmless and stayed wrong: the agreement was load-bearing
 * with nothing enforcing it, and any future path that writes one without the
 * other would have turned a lifecycle decision into a coin toss.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class EmployerArchiveFollowsTheDatedAssignmentIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private EmployerService employerService;
    @Autowired private EmployerRepository employers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void authenticateAsAnAdministrator() {
        String username = "arch-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Archive Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));
    }

    // ── 1: someone still belongs to it ─────────────────────────────────────

    @Test
    @DisplayName("1: an employer with a member assigned today cannot be archived")
    void aCurrentAssignmentBlocksArchiving() {
        long a = employer("A");
        long member = member("M", a);
        assign(member, a, LocalDate.now().minusMonths(6), null);

        assertThatThrownBy(() -> employerService.archive(a))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(isActive(a)).isTrue();
    }

    // ── 2: they used to, and moved on ──────────────────────────────────────

    @Test
    @DisplayName("2: a member who moved to another employer does not keep the old one alive")
    void aClosedAssignmentDoesNotBlockArchiving() {
        long a = employer("A");
        long b = employer("B");
        long member = member("M", b);

        // [six months ago, last month) with A, then B from last month.
        assign(member, a, LocalDate.now().minusMonths(6), LocalDate.now().minusMonths(1));
        assign(member, b, LocalDate.now().minusMonths(1), null);

        endPoliciesOf(a);

        assertThatCode(() -> employerService.archive(a))
                .as("an employer nobody belongs to any more is exactly what archiving is for")
                .doesNotThrowAnyException();
        assertThat(isActive(a)).isFalse();
    }

    // ── 3 and 4: which source did it read? ─────────────────────────────────

    @Test
    @DisplayName("3: the pointer says B, the assignment says A -- archiving A is blocked")
    void theGuardFollowsTheAssignmentWhenThePointerHasMovedOn() {
        long a = employer("A");
        long b = employer("B");
        long member = member("M", a);

        // The member genuinely belongs to A: an open assignment, today.
        assign(member, a, LocalDate.now().minusMonths(2), null);

        // The pointer is made to disagree -- stale, or written by a path that
        // did not go through the resolver.
        jdbc.update("UPDATE members SET employer_id = ? WHERE id = ?", b, member);

        // The fixture's policy is ended so the member assignment is the ONLY
        // thing that can block archiving. Without this the case passed under
        // the old pointer-reading guard too -- blocked by the policy, for a
        // reason it does not claim to be testing.
        endPoliciesOf(a);

        assertThatThrownBy(() -> employerService.archive(a))
                .as("a guard reading members.employer_id would find nobody in A and archive it, "
                        + "while a member is still assigned to it")
                .isInstanceOf(BusinessRuleException.class);
        assertThat(isActive(a)).isTrue();
    }

    @Test
    @DisplayName("4: the pointer says A, the assignment says B -- archiving A is allowed")
    void theGuardIgnoresAStalePointerThatStillNamesTheEmployer() {
        long a = employer("A");
        long b = employer("B");
        long member = member("M", b);

        // The member left A last month and belongs to B.
        assign(member, a, LocalDate.now().minusMonths(6), LocalDate.now().minusMonths(1));
        assign(member, b, LocalDate.now().minusMonths(1), null);

        // The pointer is left behind, still naming A.
        jdbc.update("UPDATE members SET employer_id = ? WHERE id = ?", a, member);

        endPoliciesOf(a);

        assertThatCode(() -> employerService.archive(a))
                .as("a guard reading members.employer_id would refuse forever, on the strength of a "
                        + "value nothing dated agrees with")
                .doesNotThrowAnyException();
        assertThat(isActive(a)).isFalse();
    }

    // ── and the other half of the guard is unchanged ───────────────────────

    @Test
    @DisplayName("an inactive member with a current assignment does not block archiving")
    void anInactiveMemberDoesNotBlockArchiving() {
        long a = employer("A");
        long member = member("M", a);
        assign(member, a, LocalDate.now().minusMonths(2), null);
        jdbc.update("UPDATE members SET active = false, status = 'TERMINATED' WHERE id = ?", member);

        endPoliciesOf(a);

        assertThatCode(() -> employerService.archive(a))
                .as("belonging and being active are two conditions, and archiving needs both to fail")
                .doesNotThrowAnyException();
    }

    // ── fixture ────────────────────────────────────────────────────────────

    private long employer(String label) {
        String s = suffix();
        return jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "ARCH-" + label + "-" + s, "جهة " + label + " " + s);
    }

    /**
     * An ACTIVE member must carry a policy -- chk_active_member_requires_policy.
     * The fixture obeys it rather than working around it: a member shaped
     * differently from the ones in the database would prove nothing about a
     * guard that runs against real ones.
     */
    private long member(String label, long employerId) {
        String s = suffix();
        Long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'ACTIVE') RETURNING id",
                Long.class, "وثيقة " + label + " " + s, "ARCH-POL-" + s, employerId,
                LocalDate.now().withDayOfYear(1), LocalDate.now().withMonth(12).withDayOfMonth(31));

        return jdbc.queryForObject(
                "INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id, status, active)"
                        + " VALUES (?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, "عضو " + label + " " + s, "ARCH-M-" + s, employerId, policyId);
    }

    /**
     * The policy the fixture creates would itself block archiving, which is the
     * OTHER half of the guard and not what these cases are about. Ended here so
     * each case tests exactly the member condition it names.
     */
    private void endPoliciesOf(long employerId) {
        jdbc.update("UPDATE benefit_policies SET active = false, status = 'DRAFT' WHERE employer_id = ?",
                employerId);
    }

    private void assign(long memberId, long employerId, LocalDate from, LocalDate to) {
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id,"
                + " assignment_start_date, assignment_end_date, assignment_reason, assignment_source)"
                + " VALUES (?, ?, ?, ?, 'تجهيز اختبار الأرشفة', 'MANUAL')",
                memberId, employerId, from, to);
    }

    private boolean isActive(long employerId) {
        return Boolean.TRUE.equals(employers.findById(employerId).orElseThrow().getActive());
    }
}
