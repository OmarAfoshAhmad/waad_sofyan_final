package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import com.waad.tba.modules.member.service.MemberLimitOverviewService;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Who may read a member's ceiling is decided by MEMBER_LIMIT_VIEW, and by
 * nothing else.
 *
 * It used to be decided twice. The permission catalogue granted
 * MEMBER_LIMIT_VIEW to DATA_ENTRY, and a separate rule in the access policy
 * allowed only SUPER_ADMIN and EMPLOYER_ADMIN through. The two disagreed, and
 * the disagreement was invisible from the front end, which can only read the
 * catalogue: it offered a ceiling column that was refused on every page load.
 *
 * A rule expressed as a role also cannot be granted. An administrator who
 * decides one data-entry clerk should see ceilings had nowhere to say so --
 * the permission existed, and the server ignored it. These tests pin the
 * grant, the revocation, and the scope that still applies on top of both.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class CeilingAccessFollowsThePermissionNotTheRoleIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberLimitOverviewService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static final String YEAR_START = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String YEAR_END =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private long employerId;
    private long otherEmployerId;
    private long memberId;
    private long otherMemberId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @BeforeEach
    void seed() {
        employerId = newEmployer();
        otherEmployerId = newEmployer();
        memberId = memberUnder(employerId);
        otherMemberId = memberUnder(otherEmployerId);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private long newEmployer() {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Perm Employer " + s + "', 'PRM-" + s + "', true) RETURNING id", Long.class);
    }

    private long memberUnder(long employer) {
        String s = suffix();
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, "
                + "employer_id, start_date, end_date, annual_limit, default_coverage_percent, "
                + "status, active) VALUES ('Perm Policy', ?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 80, 'ACTIVE', true) RETURNING id",
                Long.class, "PRM-" + s, employer, new BigDecimal("60000.00"));
        Long id = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Perm Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "PRM-" + s, employer, policyId);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')",
                id, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", id, employer);
        return id;
    }

    private Long signInAs(String userType, Long employer) {
        String username = "perm-" + suffix();
        var user = userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Perm User")
                .email(username + "@waad.ly").userType(userType)
                .employerId(employer).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
        return user.getId();
    }

    /** The exceptional grant or revocation an administrator records for one user. */
    private void override(Long userId, String permission, String effect) {
        jdbc.update("INSERT INTO rbac_user_permission_overrides (user_id, permission_code, effect, "
                + "reason, changed_by) VALUES (?, ?, ?, 'قرار إداري في الاختبار', ?) "
                + "ON CONFLICT (user_id, permission_code) DO UPDATE SET effect = EXCLUDED.effect",
                userId, permission, effect, userId);
    }

    private void grant(Long userId, String permission) {
        override(userId, permission, "GRANT");
    }

    private void revoke(Long userId, String permission) {
        override(userId, permission, "REVOKE");
    }

    @Test
    @DisplayName("a data-entry user with the role default is refused, and the UI reads the same bit")
    void dataEntryByDefaultIsRefused() {
        signInAs("DATA_ENTRY", employerId);

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(memberId)))
                .as("the role enters identity, employer and policy; consumed and "
                        + "remaining limits are not part of that")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    @DisplayName("the same user, granted MEMBER_LIMIT_VIEW by an administrator, is served")
    void dataEntryWithAnExplicitGrantIsServed() {
        Long userId = signInAs("DATA_ENTRY", employerId);
        grant(userId, "MEMBER_LIMIT_VIEW");

        assertThat(service.authorizedSummariesFor(List.of(memberId)))
                .as("a rule written as a role cannot be granted to one person; this is "
                        + "the whole reason for moving it onto the permission")
                .containsKey(memberId);
    }

    @Test
    @DisplayName("revoking it closes the door again, without waiting for anything")
    void revokingTheGrantRefusesAgain() {
        Long userId = signInAs("DATA_ENTRY", employerId);
        grant(userId, "MEMBER_LIMIT_VIEW");
        assertThat(service.authorizedSummariesFor(List.of(memberId))).containsKey(memberId);

        revoke(userId, "MEMBER_LIMIT_VIEW");

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(memberId)))
                .as("an administrator taking a permission away must close the door the "
                        + "same day, so the effective set is read per request rather than "
                        + "cached into the session")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    @DisplayName("an employer administrator holding the permission still sees only their own employer")
    void anEmployerAdminIsStillBoundedByScope() {
        signInAs("EMPLOYER_ADMIN", employerId);

        assertThat(service.authorizedSummariesFor(List.of(memberId))).containsKey(memberId);
        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(memberId, otherMemberId)))
                .as("permission answers 'may you read ceilings at all'; scope answers "
                        + "'whose'. Passing the first does not answer the second")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    @DisplayName("a super administrator reads across employers")
    void aSuperAdminReadsEverything() {
        signInAs("SUPER_ADMIN", null);

        assertThatCode(() -> service.authorizedSummariesFor(List.of(memberId, otherMemberId)))
                .doesNotThrowAnyException();
        assertThat(service.authorizedSummariesFor(List.of(memberId, otherMemberId)))
                .containsKeys(memberId, otherMemberId);
    }

    @Test
    @DisplayName("revoking it from an employer administrator refuses them too -- no role is above the check")
    void revokingItFromAnEmployerAdminRefusesThemToo() {
        Long userId = signInAs("EMPLOYER_ADMIN", employerId);
        assertThat(service.authorizedSummariesFor(List.of(memberId))).containsKey(memberId);

        revoke(userId, "MEMBER_LIMIT_VIEW");

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(memberId)))
                .as("the old rule let EMPLOYER_ADMIN through by name, so a revocation "
                        + "against that role was a note in a table that changed nothing")
                .isInstanceOf(MemberAccessDeniedException.class);
    }
}
