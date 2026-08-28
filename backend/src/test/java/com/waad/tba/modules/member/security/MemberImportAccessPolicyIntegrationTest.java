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
import org.springframework.data.domain.PageRequest;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.member.entity.MemberImportBatchRow;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
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
    @Autowired private MemberImportLogRepository importLogRepository;
    @Autowired private MemberImportBatchRowRepository importBatchRowRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employer(String label) {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('IP-" + label + "-" + s
                + "', 'Import " + label + " " + s + "') RETURNING id", Long.class);
    }

    private User actingAs(String userType, Long employerId) {
        String username = "ip-" + suffix();
        User saved = userRepository.save(User.builder()
                .username(username).password("x").fullName("Import Test").email(username + "@waad.ly")
                .userType(userType).employerId(employerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
        return saved;
    }

    private void override(User user, String permission, String effect) {
        jdbc.update("insert into rbac_user_permission_overrides"
                + "(user_id,permission_code,effect,reason,changed_by) values(?,?,?,?,?)",
                user.getId(), permission, effect, "integration permission decision", user.getId());
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
                .hasMessageContaining("العمليات الخطرة");

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
    void rolesWithoutTheEffectivePermissionMayNotImportAtAll() {
        long a = employer("A");

        for (String role : new String[] {"EMPLOYER_ADMIN", "PROVIDER_STAFF", "MEDICAL_REVIEWER"}) {
            actingAs(role, a);
            assertThatThrownBy(() -> policy.require(List.of(a), false))
                    .as(role + " importing")
                    .isInstanceOf(MemberAccessDeniedException.class);
        }
    }

    @Test
    void aRoleCanBeDelegatedImportWithoutChangingItsTemplate() {
        long a = employer("A");
        User employerAdmin = actingAs("EMPLOYER_ADMIN", a);
        override(employerAdmin, "MEMBER_IMPORT", "GRANT");

        assertThat(policy.require(List.of(a), false).covers(a)).isTrue();
    }

    @Test
    void anExplicitRevocationOverridesTheDataEntryTemplate() {
        long a = employer("A");
        User dataEntry = actingAs("DATA_ENTRY", a);
        override(dataEntry, "MEMBER_IMPORT", "REVOKE");

        assertThatThrownBy(() -> policy.require(List.of(a), false))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("MEMBER_IMPORT");
    }

    @Test
    void clearingAbsentMembersFollowsTheSensitiveCapabilityNotTheRoleName() {
        long a = employer("A");
        User dataEntry = actingAs("DATA_ENTRY", a);
        override(dataEntry, "DANGER_ZONE_EXECUTE", "GRANT");

        assertThat(policy.require(List.of(a), true).mayClearAbsentMembers()).isTrue();
    }

    @Test
    void anUnscopedUserIsRefusedBeforeAnyRowIsExamined() {
        actingAs("DATA_ENTRY", null);

        assertThatThrownBy(() -> policy.require(List.of(1L), false))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void importHistoryIsRestrictedToTheUsersEmployer() {
        long a = employer("A");
        long b = employer("B");
        actingAs("DATA_ENTRY", a);

        assertThat(policy.requireHistory(List.of(a)).covers(a)).isTrue();
        assertThatThrownBy(() -> policy.requireHistory(List.of(b)))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("خارج نطاق المستخدم");
    }

    @Test
    void mixedEmployerHistoryIsNotPartiallyDisclosed() {
        long a = employer("A");
        long b = employer("B");
        actingAs("DATA_ENTRY", a);

        assertThatThrownBy(() -> policy.requireHistory(List.of(a, b)))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void legacyHistoryWithoutProvableScopeFailsClosedForScopedUsers() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a);

        assertThatThrownBy(() -> policy.requireHistory(List.of()))
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("إثبات نطاق");
    }

    @Test
    void globalImporterMayReadLegacyAndCrossEmployerHistory() {
        long a = employer("A");
        long b = employer("B");
        actingAs("SUPER_ADMIN", null);

        assertThat(policy.requireHistory(List.of()).isGlobal()).isTrue();
        assertThat(policy.requireHistory(List.of(a, b)).isGlobal()).isTrue();
    }

    @Test
    void historyRepositoryReturnsOnlyBatchesWhollyInsideTheAuthorisedScope() {
        long a = employer("A");
        long b = employer("B");
        MemberImportLog own = importLogRepository.save(MemberImportLog.builder()
                .importBatchId("history-own-" + suffix()).fileName("own.xlsx").build());
        MemberImportLog foreign = importLogRepository.save(MemberImportLog.builder()
                .importBatchId("history-foreign-" + suffix()).fileName("foreign.xlsx").build());
        MemberImportLog mixed = importLogRepository.save(MemberImportLog.builder()
                .importBatchId("history-mixed-" + suffix()).fileName("mixed.xlsx").build());

        importBatchRowRepository.save(row(own.getId(), 900001L, a));
        importBatchRowRepository.save(row(foreign.getId(), 900002L, b));
        importBatchRowRepository.save(row(mixed.getId(), 900003L, a));
        importBatchRowRepository.save(row(mixed.getId(), 900004L, b));

        var ids = importLogRepository.findVisibleToEmployers(List.of(a), PageRequest.of(0, 200))
                .map(MemberImportLog::getId).toSet();

        assertThat(ids).contains(own.getId());
        assertThat(ids).doesNotContain(foreign.getId(), mixed.getId());
    }

    private MemberImportBatchRow row(Long logId, Long memberId, long employerId) {
        return MemberImportBatchRow.builder()
                .importLogId(logId)
                .memberId(memberId)
                .action(MemberImportBatchRow.Action.CREATED)
                .importedSnapshot("{\"employerId\":" + employerId + "}")
                .build();
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
