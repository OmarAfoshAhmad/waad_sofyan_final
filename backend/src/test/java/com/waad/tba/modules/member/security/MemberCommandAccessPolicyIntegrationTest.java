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
 * WRITE access to member records.
 *
 * The policy answers "may this caller do this to this record" and stops
 * there. Whether the transition is commercially valid stays with the status
 * service, and whether a record has a footprint stays with the delete guard:
 * a permission check is not a business rule, and neither stands in for the
 * other.
 *
 * The employer judged against is always the STORED one. A caller entitled to
 * edit their own tenant's members could otherwise put another tenant's id in
 * the request body and have the check pass against the value they chose.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberCommandAccessPolicyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberCommandAccessPolicy policy;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employer(String label) {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('CP-" + label + "-" + s
                + "', 'Cmd " + label + " " + s + "') RETURNING id", Long.class);
    }

    private void actingAs(String userType, Long employerId, Long providerId) {
        String username = "cp-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Cmd Test").email(username + "@waad.ly")
                .userType(userType).employerId(employerId).providerId(providerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    private boolean allowed(MemberOperation op, Long employerId) {
        return policy.decide(op, employerId).allowed();
    }

    // -- super admin ----------------------------------------------------

    @Test
    void superAdminMayIssueEveryCommandIncludingTheSystemWideOnes() {
        long a = employer("A");
        actingAs("SUPER_ADMIN", null, null);

        for (MemberOperation op : MemberOperation.values()) {
            assertThat(allowed(op, a)).as(op + " for super admin").isTrue();
        }
    }

    // -- employer admin -------------------------------------------------

    @Test
    void anEmployerAdminMayRunTheEverydayCommandsWithinTheirOwnEmployer() {
        long a = employer("A");
        actingAs("EMPLOYER_ADMIN", a, null);

        for (MemberOperation op : new MemberOperation[] {
                MemberOperation.CREATE_MEMBER, MemberOperation.EDIT_DEMOGRAPHICS,
                MemberOperation.ADD_DEPENDENT, MemberOperation.CHANGE_STATUS,
                MemberOperation.TERMINATE, MemberOperation.REINSTATE}) {
            assertThat(allowed(op, a)).as(op + " within own employer").isTrue();
        }
    }

    @Test
    void anEmployerAdminMayNotTouchAnotherEmployersMember() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        for (MemberOperation op : new MemberOperation[] {
                MemberOperation.EDIT_DEMOGRAPHICS, MemberOperation.ADD_DEPENDENT,
                MemberOperation.CHANGE_STATUS, MemberOperation.TERMINATE}) {
            assertThat(allowed(op, b)).as(op + " against another employer").isFalse();
        }
    }

    @Test
    void anEmployerAdminMayNotHardDeleteOrRunSystemWideOperations() {
        long a = employer("A");
        actingAs("EMPLOYER_ADMIN", a, null);

        // Physical removal destroys history; the system-wide ones cross every
        // tenant by definition. Neither belongs to a single employer's
        // administrator, however legitimate their reach inside it.
        assertThat(allowed(MemberOperation.HARD_DELETE, a)).isFalse();
        assertThat(allowed(MemberOperation.RESET_KINSHIP, a)).isFalse();
        assertThat(allowed(MemberOperation.RESOLVE_DUPLICATES, a)).isFalse();
    }

    // -- data entry -----------------------------------------------------

    @Test
    void aDataEntryUserMayEnterRecordsButNotDecideTheirFate() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a, null);

        assertThat(allowed(MemberOperation.CREATE_MEMBER, a)).isTrue();
        assertThat(allowed(MemberOperation.EDIT_DEMOGRAPHICS, a)).isTrue();
        assertThat(allowed(MemberOperation.ADD_DEPENDENT, a)).isTrue();

        // Entering a record and ending someone's coverage are different acts.
        assertThat(allowed(MemberOperation.CHANGE_STATUS, a)).isFalse();
        assertThat(allowed(MemberOperation.TERMINATE, a)).isFalse();
        assertThat(allowed(MemberOperation.REINSTATE, a)).isFalse();
        assertThat(allowed(MemberOperation.HARD_DELETE, a)).isFalse();
        assertThat(allowed(MemberOperation.RESOLVE_DUPLICATES, a)).isFalse();
    }

    // -- read-only roles ------------------------------------------------

    @Test
    void providerStaffAndReviewersMayNotWriteToTheMemberRecordAtAll() {
        long a = employer("A");

        for (String role : new String[] {"PROVIDER_STAFF", "MEDICAL_REVIEWER"}) {
            actingAs(role, a, null);
            for (MemberOperation op : new MemberOperation[] {
                    MemberOperation.CREATE_MEMBER, MemberOperation.EDIT_DEMOGRAPHICS,
                    MemberOperation.ADD_DEPENDENT, MemberOperation.CHANGE_STATUS,
                    MemberOperation.TERMINATE, MemberOperation.HARD_DELETE}) {
                assertThat(allowed(op, a)).as(role + " attempting " + op).isFalse();
            }
        }
    }

    // -- bulk: all or nothing -------------------------------------------

    @Test
    void aBulkCommandIsRefusedEntirelyWhenOneElementIsOutOfScope() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        // Silently skipping the forbidden element is the dangerous outcome:
        // the caller is told the operation succeeded and never learns that
        // part of their selection was ignored.
        assertThatThrownBy(() -> policy.requireBulk(MemberOperation.BULK_OPERATION, List.of(a, b)))
                .isInstanceOf(MemberAccessDeniedException.class);

        assertThat(policy.requireBulk(MemberOperation.BULK_OPERATION, List.of(a, a)).covers(a))
                .isTrue();
    }

    @Test
    void anEmptyBulkSelectionIsRefusedRatherThanTreatedAsSuccess() {
        long a = employer("A");
        actingAs("EMPLOYER_ADMIN", a, null);

        assertThatThrownBy(() -> policy.requireBulk(MemberOperation.BULK_OPERATION, List.of()))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    // -- the handle throws rather than returning something ignorable ----

    @Test
    void requireThrowsOnRefusalAndReturnsTheScopeOnSuccess() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        assertThat(policy.require(MemberOperation.EDIT_DEMOGRAPHICS, a).covers(a)).isTrue();
        assertThatThrownBy(() -> policy.require(MemberOperation.EDIT_DEMOGRAPHICS, b))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void aMemberWithNoEmployerIsWritableOnlyUnderGlobalScope() {
        long a = employer("A");
        actingAs("EMPLOYER_ADMIN", a, null);
        assertThat(allowed(MemberOperation.EDIT_DEMOGRAPHICS, null)).isFalse();

        actingAs("SUPER_ADMIN", null, null);
        assertThat(allowed(MemberOperation.EDIT_DEMOGRAPHICS, null)).isTrue();
    }
}
