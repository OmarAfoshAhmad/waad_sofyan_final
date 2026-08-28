package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.security.MemberAccessDeniedException;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The bulk ceiling read is a page-shaped hole in the member API: it takes a
 * list of ids and hands back money for each. Everything about that shape
 * rewards probing, so the scope check is the point of this file.
 *
 * The rule under test is that an id outside the caller's scope refuses the
 * WHOLE request. Dropping it from the result instead would make "outside your
 * scope" and "no ceiling configured" both arrive as an absent key, which is
 * precisely how a loop over id ranges maps out who exists.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberLimitOverviewScopeIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberLimitOverviewService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static final String YEAR_START = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String YEAR_END =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private long ownEmployerId;
    private long otherEmployerId;
    private long ownMemberId;
    private long otherMemberId;
    private long providerId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @BeforeEach
    void seed() {
        ownEmployerId = newEmployer();
        otherEmployerId = newEmployer();
        ownMemberId = memberUnder(ownEmployerId);
        otherMemberId = memberUnder(otherEmployerId);
        String s = suffix();
        providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, "
                + "provider_type, allow_all_employers) VALUES ('Scope Prov " + s + "', 'SCLIC-" + s
                + "', 'CLINIC', true) RETURNING id", Long.class);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private long newEmployer() {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Scope Employer " + s + "', 'SC-" + s + "', true) RETURNING id", Long.class);
    }

    private long memberUnder(long employerId) {
        String s = suffix();
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, "
                + "employer_id, start_date, end_date, annual_limit, default_coverage_percent, "
                + "status, active) VALUES ('Scope Policy', ?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 80, 'ACTIVE', true) RETURNING id",
                Long.class, "SC-" + s, employerId, new BigDecimal("60000.00"));
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, "
                + "employer_id, benefit_policy_id, status, active) VALUES ('Scope Member', ?, ?, ?, "
                + "'ACTIVE', true) RETURNING id", Long.class, "SC-" + s, employerId, policyId);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')",
                memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", memberId, employerId);
        return memberId;
    }

    /** Signs in a user whose reach is limited to one employer. */
    private void signInAsEmployerAdminOf(long employerId) {
        signInAs("EMPLOYER_ADMIN", employerId, null);
    }

    private void signInAs(String userType, Long employerId, Long providerId) {
        String username = "scope-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Scope User")
                .email(username + "@waad.ly").userType(userType)
                .employerId(employerId).providerId(providerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    @Test
    void aPageWithinTheCallersEmployerIsServed() {
        signInAsEmployerAdminOf(ownEmployerId);

        var summaries = service.authorizedSummariesFor(List.of(ownMemberId));

        assertThat(summaries).containsKey(ownMemberId);
    }

    @Test
    void aSingleForeignIdRefusesTheWholeRequest() {
        signInAsEmployerAdminOf(ownEmployerId);

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(ownMemberId, otherMemberId)))
                .as("refusing outright, rather than returning the one id they may see, "
                        + "is what stops the absent key from answering 'does this member exist'")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void aForeignIdOnItsOwnIsRefusedRatherThanReturnedEmpty() {
        signInAsEmployerAdminOf(ownEmployerId);

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(otherMemberId)))
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void aDataEntryUserMayNotBulkReadCeilings() {
        signInAs("DATA_ENTRY", ownEmployerId, null);

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(ownMemberId)))
                .as("the role enters identity, employer and policy; consumed and remaining "
                        + "limits are not part of that, and MemberQueryAccessPolicy has "
                        + "always said so")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void aProviderMayNotBulkReadCeilings() {
        signInAs("PROVIDER_STAFF", null, providerId);

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(ownMemberId)))
                .as("a provider holds MEMBER_LIMIT_VIEW for the patient in front of them "
                        + "and not MEMBER_LIMIT_LIST_VIEW, so the single read they need "
                        + "no longer carries a page of the insurer's book with it")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    @Test
    void anUnauthenticatedCallerGetsNothing() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.authorizedSummariesFor(List.of(ownMemberId)))
                .as("fails closed: no principal is not the same as no restriction")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void aBatchLargerThanAPageIsRefused() {
        signInAsEmployerAdminOf(ownEmployerId);

        List<Long> tooMany = new ArrayList<>();
        for (long i = 1; i <= 201; i++) {
            tooMany.add(i);
        }

        assertThatThrownBy(() -> service.authorizedSummariesFor(tooMany))
                .as("a page is the only legitimate caller, so anything larger is either "
                        + "a mistake or an extraction")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anEmptyRequestCostsNothingAndAsksNobodyForPermission() {
        SecurityContextHolder.clearContext();

        assertThat(service.authorizedSummariesFor(List.of())).isEmpty();
    }
}
