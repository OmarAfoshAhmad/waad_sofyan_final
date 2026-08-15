package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * READ access to member data, decided per operation.
 *
 * Scope answers "whose records"; this answers "and may you do THIS to them".
 * The two are separate because an open-network provider reaches every
 * employer's members without thereby being entitled to export them or read
 * their balances -- reach is a commercial fact, permission is a decision.
 *
 * Every refusal here must be a refusal, never an empty result. Telling an
 * employer administrator that another tenant has no members is a false
 * statement about data they are entitled to no statement about.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberQueryAccessPolicyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberQueryAccessPolicy policy;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employer(String label) {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('QP-" + label + "-" + s
                + "', 'Query " + label + " " + s + "') RETURNING id", Long.class);
    }

    private long provider(boolean openNetwork, Long... allowed) {
        String s = suffix();
        Long id = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type, "
                + "allow_all_employers) VALUES ('Prov " + s + "', 'QPLIC-" + s + "', 'CLINIC', "
                + openNetwork + ") RETURNING id", Long.class);
        for (Long e : allowed) {
            jdbc.update("INSERT INTO provider_allowed_employers (provider_id, employer_id) VALUES (?, ?)",
                    id, e);
        }
        return id;
    }

    /** Signs in as a user of the given shape, the way the policy will see them. */
    private void actingAs(String userType, Long employerId, Long providerId) {
        String username = "qp-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Query Test").email(username + "@waad.ly")
                .userType(userType).employerId(employerId).providerId(providerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));
    }

    // ── listing and searching ───────────────────────────────────────────

    @Test
    void anEmployerAdminListingWithoutAskingIsConstrainedToTheirOwnEmployer() {
        long a = employer("A");
        actingAs("EMPLOYER_ADMIN", a, null);

        MemberAccessDecision decision = policy.forListing(MemberOperation.LIST, null);

        assertThat(decision.allowed()).isTrue();
        // A null request must narrow to their employer, not widen to all.
        assertThat(decision.scope().employerIds()).containsExactly(a);
        assertThat(decision.scope().isGlobal()).isFalse();
    }

    @Test
    void anEmployerAdminAskingForAnotherEmployerIsRefusedNotShownAnEmptyList() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        MemberAccessDecision decision = policy.forListing(MemberOperation.LIST, b);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.operation()).isEqualTo(MemberOperation.LIST);
        assertThat(decision.reason()).isNotBlank();
        assertThatThrownBy(decision::orThrow).isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void searchingIsScopedTheSameWayAsListing() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        assertThat(policy.forListing(MemberOperation.SEARCH, null).scope().employerIds())
                .containsExactly(a);
        assertThat(policy.forListing(MemberOperation.SEARCH, b).allowed()).isFalse();
    }

    // ── a single member ─────────────────────────────────────────────────

    @Test
    void readingAMemberOfAnotherEmployerIsRefusedEvenWithADirectId() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        // Editing the id in the URL is the whole attack. The record exists,
        // so "not found" would be a lie; it is simply not theirs.
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, b).allowed()).isFalse();
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, a).allowed()).isTrue();
    }

    @Test
    void aMemberWithNoEmployerIsReachableOnlyByGlobalScope() {
        long a = employer("A");
        actingAs("EMPLOYER_ADMIN", a, null);
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, null).allowed()).isFalse();

        actingAs("SUPER_ADMIN", null, null);
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, null).allowed()).isTrue();
    }

    // ── reach is not permission ─────────────────────────────────────────

    @Test
    void anOpenNetworkProviderMayReadAMemberButMayNotExportOrReadFinancials() {
        long a = employer("A");
        long providerId = provider(true);
        actingAs("PROVIDER_STAFF", null, providerId);

        // Reach: the open network genuinely covers every employer.
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, a).allowed()).isTrue();

        // Permission: bulk extraction and detailed balances are separate
        // grants. A provider treating patients does not thereby get the
        // insurer's ledger or a file of every member in the country.
        assertThat(policy.forListing(MemberOperation.EXPORT, null).allowed()).isFalse();
        assertThat(policy.forMember(MemberOperation.VIEW_FINANCIALS, a).allowed()).isFalse();
    }

    @Test
    void aClosedNetworkProviderIsLimitedToItsContractedEmployers() {
        long a = employer("A");
        long b = employer("B");
        long providerId = provider(false, a);
        actingAs("PROVIDER_STAFF", null, providerId);

        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, a).allowed()).isTrue();
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, b).allowed()).isFalse();
    }

    // ── export ──────────────────────────────────────────────────────────

    @Test
    void exportingIsConstrainedToTheCallersOwnScope() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        assertThat(policy.forListing(MemberOperation.EXPORT, null).scope().employerIds())
                .containsExactly(a);
        // The most damaging leak in the audit: a whole file of another
        // tenant's members leaving the system.
        assertThat(policy.forListing(MemberOperation.EXPORT, b).allowed()).isFalse();
    }

    @Test
    void superAdminMayExportAcrossEmployers() {
        actingAs("SUPER_ADMIN", null, null);

        MemberAccessDecision decision = policy.forListing(MemberOperation.EXPORT, null);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.scope().isGlobal()).isTrue();
    }

    // ── denial is uniform and carries its reason ────────────────────────

    @Test
    void aDeniedScopeRefusesEveryReadOperationWithTheOperationNamed() {
        actingAs("MEDICAL_REVIEWER", null, null);

        for (MemberOperation op : new MemberOperation[] {
                MemberOperation.LIST, MemberOperation.SEARCH, MemberOperation.EXPORT}) {
            MemberAccessDecision d = policy.forListing(op, null);
            assertThat(d.allowed()).as(op + " must be refused").isFalse();
            assertThat(d.operation()).isEqualTo(op);
            assertThat(d.reason()).isNotBlank();
        }
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, 1L).allowed()).isFalse();
    }

    @Test
    void aRefusalIsAnExceptionRatherThanAValueACallerCanIgnore() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        // orThrow exists so a call site that forgets to check the decision
        // fails closed instead of proceeding on a value it ignored.
        assertThatThrownBy(() -> policy.forMember(MemberOperation.VIEW_FINANCIALS, b).orThrow())
                .isInstanceOf(MemberAccessDeniedException.class)
                .hasMessageContaining("نطاق");
    }

    // ── data entry: identity yes, money and bulk no ─────────────────────

    @Test
    void aDataEntryUserReadsDescriptiveDataWithinTheirOwnEmployer() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a, null);

        // The role enters identity, employer, policy and basic eligibility.
        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, a).allowed()).isTrue();
        assertThat(policy.forListing(MemberOperation.LIST, null).scope().employerIds())
                .containsExactly(a);
    }

    @Test
    void aDataEntryUserMayNotReadTheFinancialSummary() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a, null);

        // Consumed and remaining limits are not what data entry is for, and
        // with no fine-grained permission to lean on, refusal is the safe
        // default rather than a role quietly wider than its name.
        MemberAccessDecision decision = policy.forMember(MemberOperation.VIEW_FINANCIALS, a);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("المالية");
    }

    @Test
    void aDataEntryUserMayNotExportTheMemberList() {
        long a = employer("A");
        actingAs("DATA_ENTRY", a, null);

        assertThat(policy.forListing(MemberOperation.EXPORT, null).allowed()).isFalse();
    }

    @Test
    void aDataEntryUserCannotReachAnotherEmployerByIdOrByNull() {
        long a = employer("A");
        long b = employer("B");
        actingAs("DATA_ENTRY", a, null);

        assertThat(policy.forMember(MemberOperation.VIEW_DETAILS, b).allowed()).isFalse();
        assertThat(policy.forListing(MemberOperation.LIST, b).allowed()).isFalse();
        // A null request narrows to their own employer; it never widens.
        assertThat(policy.forListing(MemberOperation.LIST, null).scope().isGlobal()).isFalse();
    }

    // ── the authorised handle ───────────────────────────────────────────

    @Test
    void requireListingReturnsTheScopeAQueryMustUseAndThrowsOtherwise() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        AuthorizedMemberScope authorized = policy.requireListing(MemberOperation.LIST, null);

        // The permission and the data the query needs are the same object, so
        // a caller cannot build the filter without having been authorised.
        assertThat(authorized.isGlobal()).isFalse();
        assertThat(authorized.employerIds()).containsExactly(a);

        assertThatThrownBy(() -> policy.requireListing(MemberOperation.LIST, b))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void requireMemberThrowsRatherThanReturningSomethingIgnorable() {
        long a = employer("A");
        long b = employer("B");
        actingAs("EMPLOYER_ADMIN", a, null);

        assertThat(policy.requireMember(MemberOperation.VIEW_DETAILS, a).covers(a)).isTrue();
        assertThatThrownBy(() -> policy.requireMember(MemberOperation.VIEW_DETAILS, b))
                .isInstanceOf(MemberAccessDeniedException.class);
    }
}
