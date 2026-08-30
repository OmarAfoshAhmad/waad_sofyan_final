package com.waad.tba.modules.benefitpolicy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * P-04 (EMPLOYER_ADMIN slice): {@code BenefitPolicyController} enforces
 * employer scope itself ({@code currentEmployerScope}, {@code
 * scopedEmployerId}, {@code assertEmployerScope}), not through
 * {@code MemberAccessScopeResolver} -- documented as a P0 finding in
 * CORE_DOMAIN_CLOSURE.md because it silently grants unscoped reads to
 * ACCOUNTANT/MEDICAL_REVIEWER. That part stays OPEN pending a production
 * data audit. This test covers the part that is NOT blocked by that
 * question: whether the controller's own check actually holds for
 * EMPLOYER_ADMIN, the one role it was explicitly written for. No test
 * existed for this controller before this round -- the check could have
 * been silently broken by any later edit and nothing would have caught it.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BenefitPolicyControllerEmployerScopeIsolationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitPolicyController controller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employerA;
    private long employerB;
    private long policyA;
    private long policyB;

    @BeforeEach
    void seedTwoEmployersEachWithOnePolicy() {
        String s = suffix();
        employerA = jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "PSCOPE-A-" + s, "جهة أ لعزل النطاق " + s);
        employerB = jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "PSCOPE-B-" + s, "جهة ب لعزل النطاق " + s);
        policyA = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status, active)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'DRAFT', true) RETURNING id",
                Long.class, "وثيقة أ " + s, "PSCOPE-POL-A-" + s, employerA,
                LocalDate.now(), LocalDate.now().plusYears(1));
        policyB = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status, active)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'DRAFT', true) RETURNING id",
                Long.class, "وثيقة ب " + s, "PSCOPE-POL-B-" + s, employerB,
                LocalDate.now(), LocalDate.now().plusYears(1));
    }

    private void actAsEmployerAdmin(long employerId, String username) {
        users.save(User.builder().username(username).password("x").fullName("Employer Admin")
                .email(username + "@waad.ly").userType("EMPLOYER_ADMIN").employerId(employerId).active(true)
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "x", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYER_ADMIN"))));
    }

    @Test
    @DisplayName("an EMPLOYER_ADMIN scoped to A reads A's policy and is denied B's, by id")
    void employerAdminReadsOwnPolicyAndIsDeniedTheOthersById() {
        actAsEmployerAdmin(employerA, "pscope-a-" + suffix());

        assertThat(controller.findById(policyA).getBody().getData().getId()).isEqualTo(policyA);

        assertThatThrownBy(() -> controller.findById(policyB))
                .as("A's admin must not read B's policy just by knowing its id")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an EMPLOYER_ADMIN scoped to A cannot list policies for employer B")
    void employerAdminCannotListAnotherEmployersPolicies() {
        actAsEmployerAdmin(employerA, "pscope-a-" + suffix());

        assertThat(controller.findByEmployer(employerA).getBody().getData())
                .extracting(dto -> dto.getId())
                .containsExactly(policyA);

        assertThatThrownBy(() -> controller.findByEmployer(employerB))
                .as("requesting another employer's policy list outright, not just filtering it away")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an EMPLOYER_ADMIN's own paginated list never contains another employer's policy, "
            + "even when they explicitly ask for it")
    void employerAdminPaginatedListIgnoresARequestedForeignEmployerId() {
        actAsEmployerAdmin(employerA, "pscope-a-" + suffix());

        var page = controller.findAll(employerB, 0, 20, "createdAt", "DESC", "", null).getBody().getData();

        assertThat(page.getContent())
                .as("the controller overrides any requested employerId with the caller's own scope -- "
                        + "it must never let A's admin read B's list by passing B's id as a parameter")
                .extracting(dto -> dto.getId())
                .containsExactly(policyA);
    }

    @Test
    @DisplayName("a SUPER_ADMIN is not scoped: both policies are reachable")
    void superAdminReachesBothEmployersPolicies() {
        String username = "pscope-admin-" + suffix();
        users.save(User.builder().username(username).password("x").fullName("Super Admin")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "x", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        assertThat(controller.findById(policyA).getBody().getData().getId()).isEqualTo(policyA);
        assertThat(controller.findById(policyB).getBody().getData().getId()).isEqualTo(policyB);
    }
}
