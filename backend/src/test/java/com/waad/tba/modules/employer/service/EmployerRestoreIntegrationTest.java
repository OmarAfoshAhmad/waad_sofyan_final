package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
 * E-06: restore is an explicit, guarded, audited transition -- not the
 * inverse of a flag flip.
 *
 * archive() and restore() were setters with a validation check bolted on.
 * Neither refused to run against an employer already in the state it was
 * asking for, and neither left a record anywhere but a log line -- which is
 * not something anyone can query six months later to answer "who reactivated
 * this employer, and when".
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class EmployerRestoreIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private EmployerService employerService;
    @Autowired private EmployerRepository employers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void authenticateAsAnAdministrator() {
        String username = "restore-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Restore Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));
    }

    private long employer() {
        String s = suffix();
        return jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "RST-" + s, "جهة استعادة " + s);
    }

    @Test
    @DisplayName("restoring an already-active employer is refused, not a silent no-op")
    void restoringAnActiveEmployerIsRefused() {
        long id = employer();

        assertThatThrownBy(() -> employerService.restore(id))
                .as("the operator asked for a transition that cannot happen; reporting success "
                        + "would hide that from a bulk result, the one place they would see it")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("archiving an already-archived employer is refused, not a silent no-op")
    void archivingAnArchivedEmployerIsRefused() {
        long id = employer();
        employerService.archive(id);

        assertThatThrownBy(() -> employerService.archive(id))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("archiving is recorded in the audit trail, with who and what")
    void archivingIsAudited() {
        long id = employer();
        employerService.archive(id);

        var rows = jdbc.queryForList(
                "SELECT reason FROM medical_audit_logs WHERE entity_type = 'EMPLOYER'"
                        + " AND entity_id = ? ORDER BY id DESC LIMIT 1", String.valueOf(id));

        assertThat(rows)
                .as("archiving hides an employer from every list in the system; that has to be "
                        + "queryable afterwards, not just logged to a file")
                .hasSize(1);
        assertThat(rows.get(0).get("reason")).asString().contains("أرشفة");
    }

    @Test
    @DisplayName("restoring is recorded in the audit trail")
    void restoringIsAudited() {
        long id = employer();
        employerService.archive(id);
        employerService.restore(id);

        var rows = jdbc.queryForList(
                "SELECT reason FROM medical_audit_logs WHERE entity_type = 'EMPLOYER' AND entity_id = ?"
                        + " AND action = 'RESTORED'", String.valueOf(id));

        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("restore over capacity already exceeded while archived is refused")
    void restoreOverExceededCapacityIsRefused() {
        long id = employer();
        jdbc.update("UPDATE employers SET max_member_limit = 1 WHERE id = ?", id);
        employerService.archive(id);

        // Two members attached while archived, both exceeding the cap.
        long policyId = policy(id);
        member(id, policyId, "1");
        member(id, policyId, "2");

        assertThatThrownBy(() -> employerService.restore(id))
                .as("a cap already exceeded by members added while archived must not come back active")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("restore with an invalid contract (end before start) is refused")
    void restoreWithInvalidContractIsRefused() {
        long id = employer();
        employerService.archive(id);
        jdbc.update("UPDATE employers SET contract_start_date = ?, contract_end_date = ? WHERE id = ?",
                LocalDate.now(), LocalDate.now().minusDays(1), id);

        assertThatThrownBy(() -> employerService.restore(id))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a valid restore succeeds and the employer is active again")
    void aValidRestoreSucceeds() {
        long id = employer();
        employerService.archive(id);

        assertThatCode(() -> employerService.restore(id)).doesNotThrowAnyException();
        assertThat(employers.findById(id).orElseThrow().getActive()).isTrue();
    }

    private long policy(long employerId) {
        String s = suffix();
        return jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'ACTIVE') RETURNING id",
                Long.class, "وثيقة " + s, "RST-POL-" + s, employerId,
                LocalDate.now().withDayOfYear(1), LocalDate.now().withMonth(12).withDayOfMonth(31));
    }

    private void member(long employerId, long policyId, String tag) {
        String s = suffix();
        long memberId = jdbc.queryForObject(
                "INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id, status, active)"
                        + " VALUES (?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, "عضو " + tag + " " + s, "RST-M" + tag + s, employerId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id,"
                + " assignment_start_date, assignment_reason, assignment_source)"
                + " VALUES (?, ?, ?, 'تجهيز اختبار الاستعادة', 'MANUAL')",
                memberId, employerId, LocalDate.now().minusDays(1));
    }
}
