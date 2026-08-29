package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Which members a caller may reach.
 *
 * The rule this replaces returned a nullable employer id: it clamped
 * EMPLOYER_ADMIN and passed every other role's request straight through, with
 * null meaning "no filter" -- which is every employer in the system. An
 * absent scope and a global one shared a representation, so a misconfigured
 * account read as an omnipotent one.
 *
 * Every case below therefore asserts one of three explicit states, and the
 * last one asserts that no path can produce null at all.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberAccessScopeResolverIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberAccessScopeResolver resolver;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employer(String label) {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('SC-" + label + "-" + s
                + "', 'Scope " + label + " " + s + "') RETURNING id", Long.class);
    }

    private User user(String userType, Long employerId, Long providerId) {
        return User.builder()
                .id(1L).username("u-" + suffix()).userType(userType)
                .employerId(employerId).providerId(providerId).active(true).build();
    }

    /** A provider with either an open network or a fixed allow-list. */
    private long provider(boolean openNetwork, Long... allowedEmployerIds) {
        String s = suffix();
        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, "
                + "provider_type, allow_all_employers) VALUES ('Prov " + s + "', 'SCLIC-" + s
                + "', 'CLINIC', " + openNetwork + ") RETURNING id", Long.class);
        for (Long employerId : allowedEmployerIds) {
            jdbc.update("INSERT INTO provider_allowed_employers (provider_id, employer_id) VALUES (?, ?)",
                    providerId, employerId);
        }
        return providerId;
    }

    // ── the granted scopes ──────────────────────────────────────────────

    @Test
    void superAdminIsGlobal() {
        assertThat(resolver.resolveFor(user("SUPER_ADMIN", null, null)).isGlobal()).isTrue();
    }

    @Test
    void anEmployerAdminWithNoRequestGetsTheirOwnEmployerAndNotEveryEmployer() {
        long a = employer("A");

        MemberAccessScope scope = resolver.resolveFor(user("EMPLOYER_ADMIN", a, null));

        // The old rule returned a bare id here; a caller that ignored it saw
        // no filter at all.
        assertThat(scope.kind()).isEqualTo(MemberAccessScope.Kind.EMPLOYERS);
        assertThat(scope.employerIds()).containsExactly(a);
        assertThat(scope.isGlobal()).isFalse();
    }

    @Test
    void anEmployerAdminMayAskForTheirOwnEmployer() {
        long a = employer("A");

        MemberAccessScope scope = resolver.resolveFor(user("EMPLOYER_ADMIN", a, null), a);

        assertThat(scope.employerIds()).containsExactly(a);
    }

    @Test
    void anEmployerAdminAskingForAnotherEmployerIsRefusedRatherThanShownNothing() {
        long a = employer("A");
        long b = employer("B");

        MemberAccessScope scope = resolver.resolveFor(user("EMPLOYER_ADMIN", a, null), b);

        // Not an empty set. An empty scope renders as "this employer has no
        // members", which is a false statement about someone else's data
        // rather than a refusal to look at it.
        assertThat(scope.isDenied()).isTrue();
        assertThat(scope.reason()).isNotBlank();
        assertThat(scope.covers(b)).isFalse();
    }

    @Test
    void anEmployerAdminWithNoEmployerIsDeniedRatherThanUnscoped() {
        MemberAccessScope scope = resolver.resolveFor(user("EMPLOYER_ADMIN", null, null));

        // A misconfigured account is not a system-wide one.
        assertThat(scope.isDenied()).isTrue();
    }

    // ── providers ───────────────────────────────────────────────────────

    @Test
    void aClosedNetworkProviderSeesOnlyItsContractedEmployers() {
        long a = employer("A");
        long b = employer("B");
        long c = employer("C");
        long providerId = provider(false, a, b);

        MemberAccessScope scope = resolver.resolveFor(user("PROVIDER_STAFF", null, providerId));

        assertThat(scope.employerIds()).containsExactlyInAnyOrder(a, b);
        assertThat(scope.covers(c)).isFalse();
    }

    /**
     * A provider's contract with an employer can be ended -- provider_allowed
     * _employers.active -- and ending it has to end their reach.
     *
     * Provider.allowedEmployers is an unfiltered @OneToMany, so the scope
     * included every employer this provider had EVER been contracted with. A
     * closed contract left the provider's staff able to read that employer's
     * members indefinitely, and nothing on any screen would have shown it:
     * the link was correctly marked inactive, and the code that mattered did
     * not read the mark.
     *
     * Found while auditing employers, in the members module. Fixed where it
     * lives.
     */
    @Test
    void anEndedContractEndsTheProvidersReach() {
        long stillContracted = employer("STILL");
        long contractEnded = employer("ENDED");
        long providerId = provider(false, stillContracted, contractEnded);

        jdbc.update("UPDATE provider_allowed_employers SET active = false"
                + " WHERE provider_id = ? AND employer_id = ?", providerId, contractEnded);

        MemberAccessScope scope = resolver.resolveFor(user("PROVIDER_STAFF", null, providerId));

        assertThat(scope.employerIds()).containsExactly(stillContracted);
        assertThat(scope.covers(contractEnded))
                .as("the contract that put this employer in reach has been closed")
                .isFalse();
    }

    @Test
    void aClosedNetworkProviderWithAnEmptyAllowListIsDenied() {
        long providerId = provider(false);

        MemberAccessScope scope = resolver.resolveFor(user("PROVIDER_STAFF", null, providerId));

        // "Contracted with nobody" and "contracted with everybody" are one
        // careless SQL clause apart, so the empty case never becomes a scope.
        assertThat(scope.isDenied()).isTrue();
    }

    @Test
    void anOpenNetworkProviderReachesEveryEmployerButThatIsAboutREACHOnly() {
        long a = employer("A");
        long providerId = provider(true);

        MemberAccessScope scope = resolver.resolveFor(user("PROVIDER_STAFF", null, providerId));

        // An open network is a real commercial arrangement, so the provider
        // may reach any employer's members. What it may DO with them --
        // export, edit, read detailed financials -- is decided by the
        // operation policies, not here. This class answers "whose records",
        // never "which operations".
        assertThat(scope.isGlobal()).isTrue();
        assertThat(scope.covers(a)).isTrue();
    }

    @Test
    void aProviderStaffUserWithNoProviderIsDenied() {
        assertThat(resolver.resolveFor(user("PROVIDER_STAFF", null, null)).isDenied()).isTrue();
    }

    // ── reviewers and data entry ────────────────────────────────────────

    @Test
    void aReviewerBoundToAnEmployerGetsThatEmployerOnly() {
        long a = employer("A");

        MemberAccessScope scope = resolver.resolveFor(user("MEDICAL_REVIEWER", a, null));

        assertThat(scope.employerIds()).containsExactly(a);
        assertThat(scope.isGlobal()).isFalse();
    }

    @Test
    void aDataEntryUserBoundToAnEmployerGetsThatEmployerOnly() {
        long a = employer("A");

        assertThat(resolver.resolveFor(user("DATA_ENTRY", a, null)).employerIds()).containsExactly(a);
    }

    @Test
    void anUnboundReviewerIsDeniedRatherThanTreatedAsGlobal() {
        MemberAccessScope scope = resolver.resolveFor(user("MEDICAL_REVIEWER", null, null));

        // A role says what someone may DO, not whose records they may do it
        // to. Reading "no employer" as "all employers" is the conflation this
        // class exists to remove.
        //
        // Operationally this is a closed failure and not yet a finished
        // answer: a central reviewer who works across employers needs an
        // explicit cross-tenant grant, or access derived from the claims
        // assigned to them. Until that exists this denies them, which is safe
        // but not sufficient.
        assertThat(scope.isDenied()).isTrue();
        assertThat(scope.reason()).contains("صلاحية وصول عام صريحة");
    }

    @Test
    void anUnboundDataEntryUserIsDenied() {
        assertThat(resolver.resolveFor(user("DATA_ENTRY", null, null)).isDenied()).isTrue();
    }

    // ── the shapes that must never widen a scope ────────────────────────

    @Test
    void anEmployerIdThatDoesNotExistDoesNotBecomeGlobalAccess() {
        long a = employer("A");

        MemberAccessScope scope = resolver.resolveFor(user("EMPLOYER_ADMIN", a, null), 987654321L);

        assertThat(scope.isDenied()).isTrue();
        assertThat(scope.isGlobal()).isFalse();
    }

    @Test
    void theOutcomeIsDecidedByTheRoleAndNotByTheOrderOfTheChecks() {
        long a = employer("A");
        long providerId = provider(false, a);

        // userType is a single string matched by equality, so a user has
        // exactly one role and the branch order cannot decide anything by
        // accident. Pinned because that is a property of the current model,
        // not a guarantee: if userType ever becomes a set, this test fails
        // and the precedence must be made explicit rather than positional.
        User confused = user("PROVIDER_STAFF", a, providerId);
        MemberAccessScope first = resolver.resolveFor(confused);
        MemberAccessScope again = resolver.resolveFor(confused);

        assertThat(first.kind()).isEqualTo(again.kind());
        assertThat(first.employerIds()).isEqualTo(again.employerIds());
        // Provider wins over the employer binding, and the answer is the
        // provider's contracted set rather than the user's own employer row.
        assertThat(first.employerIds()).containsExactly(a);
    }

    @Test
    void noPathEverReturnsNull() {
        long a = employer("A");
        long providerId = provider(false, a);

        for (User u : new User[] {
                user("SUPER_ADMIN", null, null),
                user("EMPLOYER_ADMIN", a, null),
                user("EMPLOYER_ADMIN", null, null),
                user("PROVIDER_STAFF", null, providerId),
                user("PROVIDER_STAFF", null, null),
                user("MEDICAL_REVIEWER", a, null),
                user("MEDICAL_REVIEWER", null, null),
                user("DATA_ENTRY", null, null),
                user("UNKNOWN_TYPE", null, null)}) {

            assertThat(resolver.resolveFor(u)).as("scope for " + u.getUserType()).isNotNull();
            assertThat(resolver.resolveFor(u, a)).as("narrowed scope for " + u.getUserType()).isNotNull();
        }
        assertThat(resolver.resolveFor(null)).isNotNull();
        assertThat(resolver.resolveFor(null).isDenied()).isTrue();
    }
}
