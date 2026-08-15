package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The scope reaching actual SQL.
 *
 * A policy that is right in isolation and wrong in the WHERE clause is worth
 * nothing, so these run the converted constraint against real rows in two
 * employers rather than asserting on the object graph.
 *
 * The conversion is exhaustive over the scope's states by construction:
 * GLOBAL adds nothing, EMPLOYERS adds a non-empty IN, and DENIED has no
 * representation at all because an AuthorizedMemberScope cannot be built from
 * one. That last part is the load-bearing half -- a refusal that reached a
 * query as "no constraint" would be a cross-tenant read.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberScopeFilterIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberQueryAccessPolicy policy;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record World(long employerA, long employerB, String tag) {}

    /** Two employers, two members each, tagged so the counts are unambiguous. */
    private World world() {
        String tag = suffix();
        return new World(employerWithMembers("A", tag), employerWithMembers("B", tag), tag);
    }

    /**
     * An employer with a policy and two active members under it.
     *
     * The policy is not decoration: the database refuses an active member
     * without one, and a fixture that sidestepped that by leaving its members
     * inactive would be counting rows the real listing never returns.
     */
    private long employerWithMembers(String label, String tag) {
        String s = suffix();
        long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('SF-"
                + label + "-" + s + "', 'Filter " + label + " " + tag + "') RETURNING id", Long.class);
        long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, "
                + "employer_id, annual_limit, default_coverage_percent, start_date, end_date, status, "
                + "active) VALUES ('SFP-" + s + "', 'SFPOL-" + s + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id",
                Long.class);

        for (int i = 0; i < 2; i++) {
            String m = suffix();
            jdbc.update("INSERT INTO members (employer_id, benefit_policy_id, full_name, card_number, "
                    + "barcode, status, active) VALUES (?, ?, ?, ?, ?, 'ACTIVE', true)",
                    employerId, policyId, "Filter Member " + tag, "SF" + m, "SF" + m);
        }
        return employerId;
    }

    private void actingAs(String userType, Long employerId) {
        String username = "sf-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Filter Test").email(username + "@waad.ly")
                .userType(userType).employerId(employerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    /**
     * Runs a member count with the scope's constraint applied, as a caller
     * would -- appended unconditionally, with no null check, because the
     * converter never returns one.
     */
    private long countWithScope(AuthorizedMemberScope scope, String tag) {
        String sql = "SELECT COUNT(*) FROM members m WHERE m.full_name = 'Filter Member " + tag + "'"
                + " AND " + MemberScopeFilter.toSqlFragment(scope, "m.employer_id");
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void aGlobalScopeRestrictsNoEmployerAndSeesEveryOne() {
        World w = world();
        actingAs("SUPER_ADMIN", null);

        AuthorizedMemberScope scope = policy.requireListing(MemberOperation.LIST, null);

        // An always-true expression rather than null: a nullable "no
        // constraint" has to be tested for at every call site, and the one
        // site that forgets queries every tenant instead of failing.
        assertThat(MemberScopeFilter.toSqlFragment(scope, "m.employer_id")).isEqualTo("1 = 1");
        assertThat(countWithScope(scope, w.tag())).isEqualTo(4L);
    }

    @Test
    void anEmployerScopeConstrainsToThatEmployerAlone() {
        World w = world();
        actingAs("EMPLOYER_ADMIN", w.employerA());

        AuthorizedMemberScope scope = policy.requireListing(MemberOperation.LIST, null);

        assertThat(MemberScopeFilter.toSqlFragment(scope, "m.employer_id"))
                .isEqualTo("m.employer_id in (" + w.employerA() + ")");
        assertThat(countWithScope(scope, w.tag())).isEqualTo(2L);
    }

    @Test
    void aRefusalNeverBecomesAQueryAtAll() {
        world();
        actingAs("MEDICAL_REVIEWER", null);

        // DENIED has no SQL representation because there is no way to obtain
        // an AuthorizedMemberScope from it -- the type system stops it before
        // a WHERE clause is ever built.
        assertThatThrownBy(() -> policy.requireListing(MemberOperation.LIST, null))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void anEmployerAdminAskingForAnotherEmployerNeverReachesSql() {
        World w = world();
        actingAs("EMPLOYER_ADMIN", w.employerA());

        assertThatThrownBy(() -> policy.requireListing(MemberOperation.LIST, w.employerB()))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void aNullScopeFiltersOutEverythingRatherThanNothing() {
        World w = world();

        // Not reachable through the policy; asserted because the failure mode
        // of getting this backwards is a cross-tenant read, and "nothing" is
        // the safe direction to be wrong in.
        assertThat(MemberScopeFilter.toSqlFragment(null, "m.employer_id")).isEqualTo("1 = 0");
        assertThat(countWithScope(null, w.tag())).isZero();
    }

    @Test
    void theConstraintIsBuiltFromScopeIdsAndNeverFromRequestText() {
        World w = world();
        actingAs("EMPLOYER_ADMIN", w.employerA());

        String constraint = MemberScopeFilter.toSqlFragment(
                policy.requireListing(MemberOperation.LIST, null), "m.employer_id");

        // Only digits, commas and the column name: the ids are Longs taken
        // from the resolved scope, never strings taken from a request.
        assertThat(constraint).matches("m\\.employer_id in \\(\\d+(, \\d+)*\\)");
    }
}
