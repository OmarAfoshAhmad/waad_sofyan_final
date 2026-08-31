package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
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
import com.waad.tba.modules.benefitpolicy.controller.BenefitPolicyController;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * P-11: the benefit-policy integration gate -- one chained scenario proving
 * lifecycle, historical resolution, audit and scope hold together, rather
 * than each P-0x case checked in isolation.
 *
 * The chain:
 *   1. Employer A, policy P created as DRAFT, then activated.
 *   2. A member is enrolled under P with an open (still-current) dated
 *      assignment while P is ACTIVE.
 *   3. P is suspended.
 *   4. The member's dated assignment still resolves to P -- suspending a
 *      policy is a lifecycle change, not a rewrite of who was covered under
 *      it. Mirrors the same lifecycle-vs-history separation already proved
 *      for employers (EmployerLifecycleIsSeparateFromAuthorizationIntegrationTest).
 *   5. The audit trail for P shows CREATED, ACTIVATED and then SUSPENDED,
 *      in that order.
 *   6. EMPLOYER_ADMIN scoped to A still reads P after the suspend -- the
 *      P-04 scope check does not key off policy status.
 *   7. A suspended policy does not block a NEW overlapping policy for the
 *      same employer from being activated -- existsOverlappingActivePolicy
 *      checks status = 'ACTIVE' only, so SUSPENDED intentionally frees the
 *      window rather than permanently occupying it.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BenefitPolicyLifecycleIntegrationGateTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitPolicyService benefitPolicyService;
    @Autowired private BenefitPolicyController controller;
    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private MemberRepository members;
    @Autowired private EmployerRepository employers;
    @Autowired private com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository policies;
    @Autowired private MedicalCategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void actAsEmployerAdmin(long employerId, String username) {
        users.save(User.builder().username(username).password("x").fullName("Employer Admin")
                .email(username + "@waad.ly").userType("EMPLOYER_ADMIN").employerId(employerId).active(true)
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "x", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYER_ADMIN"))));
    }

    private void actAsSuperAdmin(String username) {
        users.save(User.builder().username(username).password("x").fullName("Super Admin")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "x", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    @Test
    @DisplayName("suspending a policy changes its lifecycle only: history, audit and scope all survive it")
    void theFullChain() throws Exception {
        String s = suffix();

        // ── 1: employer + DRAFT policy, then activated ──────────────────────
        actAsSuperAdmin("p11-admin-" + s);
        long employerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "P11-" + s, "جهة بوابة تكامل الوثيقة " + s);

        var createDto = new com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyCreateDto();
        createDto.setName("وثيقة بوابة التكامل " + s);
        createDto.setEmployerOrgId(employerId);
        createDto.setStartDate(LocalDate.now().minusMonths(1));
        createDto.setEndDate(LocalDate.now().plusYears(1));
        createDto.setAnnualLimit(new BigDecimal("20000"));
        createDto.setStatus("DRAFT");
        long policyId = benefitPolicyService.create(createDto).getId();

        MedicalCategory category = categories.save(MedicalCategory.builder()
                .code("P11-CAT-" + s).name("فئة بوابة التكامل").active(true).build());
        jdbc.update("INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id,"
                        + " encounter_type, claim_context_code, coverage_percent, inheritance_enabled, priority,"
                        + " requires_pre_approval, active, deleted, version, created_at)"
                        + " VALUES (?, ?, 'OUTPATIENT', 'OUTPATIENT', 80, false, 100, false, true, false, 0, now())",
                policyId, category.getId());

        benefitPolicyService.activate(policyId);
        assertThat(benefitPolicyService.findById(policyId).getStatus()).isEqualTo(BenefitPolicyStatus.ACTIVE);

        // The service always stamps a transition as happening TODAY --
        // realistic for this test requires the ACTIVE interval to have
        // started in the past, so that resolving a genuinely historical
        // service date and suspending today are actually two different
        // days. Backdating the open interval this way is exactly what a
        // policy that had really been active for a while looks like; it
        // does not change the mechanism under test, only when "today" is
        // relative to the member's service date.
        jdbc.update("UPDATE benefit_policy_status_history SET valid_from = ?"
                + " WHERE policy_id = ? AND valid_to IS NULL", LocalDate.now().minusDays(30), policyId);

        // ── 2: a member enrolled under P while it is ACTIVE ─────────────────
        Member member = members.save(Member.builder()
                .fullName("عضو بوابة التكامل " + s).barcode("P11-M-" + s)
                .nationalNumber("P11-NAT-" + s).employer(employers.findById(employerId).orElseThrow())
                .benefitPolicy(policies.findById(policyId).orElseThrow())
                .active(true).build());
        LocalDate historicalServiceDate = LocalDate.now().minusDays(1);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date)"
                + " VALUES (?, ?, ?)", member.getId(), policyId, historicalServiceDate.minusDays(10));
        // resolveFor also validates the member's dated EMPLOYER assignment
        // matches the policy's employer on the same date -- without this,
        // the resolution refuses for a reason unrelated to what this test
        // is proving.
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date,"
                + " assignment_reason, assignment_source) VALUES (?, ?, ?, 'تجهيز بوابة التكامل', 'MANUAL')",
                member.getId(), employerId, historicalServiceDate.minusDays(10));

        // Resolves correctly WHILE the policy is still ACTIVE, before any
        // suspend -- the baseline this test's whole point depends on.
        assertThat(policyResolver.resolveFor(member, historicalServiceDate))
                .as("sanity check: the historical service date resolves to P before it is ever suspended")
                .isPresent();

        // ── 3: P is suspended (today -- a different day than the service date)
        benefitPolicyService.suspend(policyId);
        assertThat(benefitPolicyService.findById(policyId).getStatus())
                .isEqualTo(BenefitPolicyStatus.SUSPENDED);

        // ── 4: the member's dated assignment still resolves to P ────────────
        var resolved = policyResolver.resolveFor(member, historicalServiceDate);
        assertThat(resolved)
                .as("suspending a policy TODAY must not change what a PAST service date resolves to")
                .isPresent();
        assertThat(resolved.get().getId()).isEqualTo(policyId);

        // The critical case from the discovered defect: resolving TODAY,
        // the day of the suspend itself, must now report no ACTIVE policy --
        // proving the fix does not simply always return the historical
        // answer regardless of date.
        assertThat(policyResolver.resolveFor(member, LocalDate.now()))
                .as("today -- the suspend day itself -- correctly finds no ACTIVE policy")
                .isEmpty();

        // ── 5: the audit trail shows the full lifecycle in order ────────────
        var auditActions = jdbc.queryForList(
                "SELECT action FROM medical_audit_logs WHERE entity_type = 'BENEFIT_POLICY'"
                        + " AND entity_id = ? ORDER BY id ASC",
                String.class, String.valueOf(policyId));
        assertThat(auditActions).containsExactly("CREATED", "ACTIVATED", "SUSPENDED");

        // ── 6: EMPLOYER_ADMIN scoped to A still reads P after the suspend ───
        actAsEmployerAdmin(employerId, "p11-empadmin-" + s);
        assertThatCode(() -> controller.findById(policyId))
                .as("the P-04 scope check does not key off policy status")
                .doesNotThrowAnyException();

        // ── 7: a suspended policy does not block a new overlapping ACTIVE one
        actAsSuperAdmin("p11-admin2-" + s);
        var createDto2 = new com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyCreateDto();
        createDto2.setName("وثيقة بديلة " + s);
        createDto2.setEmployerOrgId(employerId);
        createDto2.setStartDate(LocalDate.now().minusMonths(1));
        createDto2.setEndDate(LocalDate.now().plusYears(1));
        createDto2.setAnnualLimit(new BigDecimal("20000"));
        createDto2.setStatus("DRAFT");
        long secondPolicyId = benefitPolicyService.create(createDto2).getId();
        jdbc.update("INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id,"
                        + " encounter_type, claim_context_code, coverage_percent, inheritance_enabled, priority,"
                        + " requires_pre_approval, active, deleted, version, created_at)"
                        + " VALUES (?, ?, 'OUTPATIENT', 'OUTPATIENT', 80, false, 100, false, true, false, 0, now())",
                secondPolicyId, category.getId());

        assertThatCode(() -> benefitPolicyService.activate(secondPolicyId))
                .as("existsOverlappingActivePolicy only blocks on status = ACTIVE; "
                        + "SUSPENDED intentionally frees the window")
                .doesNotThrowAnyException();
        assertThat(benefitPolicyService.findById(secondPolicyId).getStatus())
                .isEqualTo(BenefitPolicyStatus.ACTIVE);
    }
}
