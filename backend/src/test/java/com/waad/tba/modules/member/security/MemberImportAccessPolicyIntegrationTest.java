package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
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
 * Who may import member data, and over which employers.
 *
 * An import is the widest write in the system: one file creates hundreds of
 * members and, with clearOldMembers, ends hundreds more. So the file is judged
 * whole. Importing the permitted rows and skipping the rest is the dangerous
 * outcome -- the caller is told it worked and never learns which people were
 * left out of the roster.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberImportAccessPolicyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberImportAccessPolicy policy;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employer(String label) {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('IP-" + label + "-" + s
                + "', 'Import " + label + " " + s + "') RETURNING id", Long.class);
    }

    private void actingAs(String userType, Long employerId) {
        String username = "ip-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Import Test").email(username + "@waad.ly")
                .userType(userType).employerId(employerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    // -- the permitted paths --------------------------------------------

    @Test
    void aDataEntryUserImportsTheirOwnEmployer() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a);

        AuthorizedImportScope authorized = policy.require(List.of(a), false);

        assertThat(authorized.covers(a)).isTrue();
        assertThat(authorized.isGlobal()).isFalse();
    }

    @Test
    void superAdminMayImportAFileSpanningSeveralEmployers() {
        long a = employer("A");
        long b = employer("B");
        actingAs("SUPER_ADMIN", null);

        AuthorizedImportScope authorized = policy.require(List.of(a, b), false);

        assertThat(authorized.isGlobal()).isTrue();
        assertThat(authorized.covers(a)).isTrue();
        assertThat(authorized.covers(b)).isTrue();
    }

    // -- one bad row refuses the file -----------------------------------

    @Test
    void aSingleRowFromAnotherEmployerRefusesTheWholeFile() {
        long a = employer("A");
        long b = employer("B");
        actingAs("DATA_ENTRY", a);

        // The last row, specifically: a policy that checked only the first
        // would pass this and then write most of the file.
        assertThatThrownBy(() -> policy.require(Arrays.asList(a, a, a, b), false))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("بالكامل");
    }

    @Test
    void aMixedFileIsRefusedForAScopedUser() {
        long a = employer("A");
        long b = employer("B");
        actingAs("DATA_ENTRY", a);

        assertThatThrownBy(() -> policy.require(List.of(a, b), false))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void aRowWithNoEmployerIsRefusedRatherThanAssumedToBeTheCallers() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a);

        // A row that names no employer is not automatically the importer's.
        // Defaulting it would put strangers into their roster on the strength
        // of a blank cell.
        assertThatThrownBy(() -> policy.require(Arrays.asList(a, null), false))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("بلا جهة عمل");
    }

    @Test
    void aFileWhoseEmployerCouldNotBeDeterminedIsRefused() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a);

        // Not an empty success: an operation whose target is unknown.
        assertThatThrownBy(() -> policy.require(List.of(), false))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("تعذر تحديد جهة العمل");
    }

    // -- ending the absentees is a separate grant ------------------------

    @Test
    void aDataEntryUserMayNotEndTheMembersAbsentFromTheFile() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a);

        // Data entry cannot change a status directly, so it must not be able
        // to end hundreds of memberships through an import checkbox either --
        // that is precisely the route around the rule.
        assertThatThrownBy(() -> policy.require(List.of(a), true))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("مدير النظام");

        // And the authorised handle says so even on the permitted path, so a
        // caller cannot read the flag from anywhere else.
        assertThat(policy.require(List.of(a), false).mayClearAbsentMembers()).isFalse();
    }

    @Test
    void superAdminMayEndTheAbsentees() {
        long a = employer("A");
        actingAs("SUPER_ADMIN", null);

        assertThat(policy.require(List.of(a), true).mayClearAbsentMembers()).isTrue();
    }

    // -- everyone else --------------------------------------------------

    @Test
    void otherRolesMayNotImportAtAll() {
        long a = employer("A");

        for (String role : new String[] {"EMPLOYER_ADMIN", "PROVIDER_STAFF", "MEDICAL_REVIEWER"}) {
            actingAs(role, a);
            assertThatThrownBy(() -> policy.require(List.of(a), false))
                    .as(role + " importing")
                    .isInstanceOf(MemberAccessDeniedException.class);
        }
    }

    @Test
    void anUnscopedUserIsRefusedBeforeAnyRowIsExamined() {
        actingAs("DATA_ENTRY", null);

        assertThatThrownBy(() -> policy.require(List.of(1L), false))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void thereIsNoPathThatReturnsWithoutAuthorisation() {
        long a = employer("A");
        long b = employer("B");
        actingAs("DATA_ENTRY", a);

        // Every refusal is an exception, so an importer cannot proceed on a
        // value it forgot to inspect.
        for (List<Long> rows : List.of(List.of(b), List.of(a, b), List.<Long>of())) {
            assertThatThrownBy(() -> policy.require(rows, false))
                    .isInstanceOf(MemberAccessDeniedException.class);
        }
        assertThat(policy.require(List.of(a), false)).isNotNull();
    }
}
