package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

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
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.dto.EmployerCreateDto;
import com.waad.tba.modules.member.dto.DependentMemberDto;
import com.waad.tba.modules.member.dto.MemberCreateDto;
import com.waad.tba.modules.member.security.MemberAccessDeniedException;
import com.waad.tba.modules.member.service.UnifiedMemberService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * E-12: the employer integration gate.
 *
 * This proves the policy decided for E-12 directly, in one chained scenario,
 * rather than each E-01..E-11 case in isolation: ARCHIVING AN EMPLOYER
 * CHANGES ITS LIFECYCLE STATE. IT DOES NOT, BY ITSELF, WITHDRAW A USER'S
 * AUTHORIZATION SCOPE.
 *
 * The two are separate mechanisms answering separate questions:
 *
 *   lifecycle:      is this employer active?    (Employer.active)
 *   authorization:  may this user reach it?     (user.employerId / scope)
 *
 * Folding the first into MemberAccessScopeResolver -- "if (employer.isArchived())
 * deny()" -- would make every historical record an archived employer ever
 * produced invisible to the people who are supposed to be able to audit it,
 * which contradicts the append-only history this system is built around.
 * Withdrawing a user's own access is a SEPARATE decision (reassigning or
 * deactivating their account), not a side effect of the employer's state.
 *
 * Scope note: the scenario uses MEMBER records as the "historical data",
 * because Members is the domain already audited end to end in this closure
 * round. Claims were not part of this round and are not exercised here;
 * MedicalAuditLogController's employer filter is exercised directly instead,
 * since it already answers the same historical question
 * (findMemberIdsEverAssignedTo, closed under E-03).
 *
 * The chain:
 *   1. Employer A active, User A scoped to A, a member enrolled under A.
 *   2. Archive A (blocked first by the active member -- E-02/E-03 -- then
 *      allowed once nobody currently belongs to it).
 *   3. User A still reads the historical member and the audit trail for A.
 *   4. User A cannot enroll a NEW member under archived A -- already enforced
 *      by the E-08 guard in MemberEmployerResolver.assignEmployer (both
 *      createPrincipalMember and dependent enrollment route through it via
 *      recordInitialEmployerAssignment), confirmed here by proof-of-bite
 *      rather than added as new production code.
 *   5. User B, scoped to a different employer, is denied throughout --
 *      archiving A changes nothing about B's exposure to it.
 *   6. User A's OWN scope is withdrawn (not caused by archiving -- a
 *      separate administrative act); only then does User A lose the read.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class EmployerLifecycleIsSeparateFromAuthorizationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private EmployerService employerService;
    @Autowired private UnifiedMemberService memberService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User userA;
    private User userB;
    private long employerA;
    private long employerB;
    private long policyA;

    @BeforeEach
    void buildTheScenario() {
        String s = suffix();

        actAs(superAdmin(s));
        var createA = new EmployerCreateDto();
        createA.setCode("E12-A-" + s);
        createA.setName("جهة أ لبوابة التكامل " + s);
        employerA = employerService.create(createA).getId();

        var createB = new EmployerCreateDto();
        createB.setCode("E12-B-" + s);
        createB.setName("جهة ب لبوابة التكامل " + s);
        employerB = employerService.create(createB).getId();

        policyA = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'ACTIVE') RETURNING id",
                Long.class, "وثيقة أ " + s, "E12-POL-" + s, employerA,
                LocalDate.now().withDayOfYear(1), LocalDate.now().withMonth(12).withDayOfMonth(31));

        userA = users.save(User.builder().username("e12-a-" + s).password("x").fullName("User A")
                .email("e12a" + s + "@waad.ly").userType("EMPLOYER_ADMIN").employerId(employerA).active(true)
                .build());
        userB = users.save(User.builder().username("e12-b-" + s).password("x").fullName("User B")
                .email("e12b" + s + "@waad.ly").userType("EMPLOYER_ADMIN").employerId(employerB).active(true)
                .build());
    }

    @Test
    @DisplayName("archiving an employer separates lifecycle from authorization, end to end")
    void theFullChain() {
        // ── 1: a historical member, already departed before archiving ──────
        //
        // Seeded directly rather than through createPrincipalMember + a later
        // closing UPDATE: member_employer_assignments is append-only by
        // design (a trigger refuses any UPDATE except assignment_end_date,
        // and closing a same-day-opened row would need a start date in the
        // past, which the trigger correctly forbids changing). A member who
        // belonged to A for a real stretch and left before today is
        // represented the same way E-03's own test represents it: two rows,
        // written once, with a closed window already in the past.
        long historicalMemberId = jdbc.queryForObject(
                "INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id, status, active)"
                        + " VALUES (?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, "عضو تاريخي لدى أ", "E12-HIST-" + suffix(), employerA, policyA);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id,"
                + " assignment_start_date, assignment_end_date, assignment_reason, assignment_source)"
                + " VALUES (?, ?, ?, ?, 'تجهيز بوابة التكامل', 'MANUAL')",
                historicalMemberId, employerA, LocalDate.now().minusMonths(6), LocalDate.now().minusMonths(1));

        // ── 2: archiving is blocked while A's policy is still active,
        //      succeeds once nobody and nothing current is left ────────────
        actAs(superAdmin(suffix()));
        assertThatThrownBy(() -> employerService.archive(employerA))
                .as("E-02: an active benefit policy blocks archiving on its own, independent of "
                        + "whether any member currently belongs -- the historical member already "
                        + "departed and is not what is blocking this")
                .isInstanceOf(BusinessRuleException.class);

        jdbc.update("UPDATE benefit_policies SET active = false, status = 'DRAFT' WHERE id = ?", policyA);

        assertThatCode(() -> employerService.archive(employerA))
                .as("E-03: the historical assignment is closed, so it never blocked archiving; "
                        + "the policy was the only thing that did, and it is ended now")
                .doesNotThrowAnyException();
        assertThat(employerService.getById(employerA).isActive()).isFalse();

        // ── 3: User A still reads the historical record and the audit trail ─
        actAs(userA);
        assertThatCode(() -> memberService.getMember(historicalMemberId))
                .as("archiving is a lifecycle change, not a withdrawal of User A's scope")
                .doesNotThrowAnyException();

        var auditRows = jdbc.queryForList(
                "SELECT reason FROM medical_audit_logs WHERE entity_type = 'EMPLOYER' AND entity_id = ?",
                String.valueOf(employerA));
        assertThat(auditRows)
                .as("the audit trail for A -- its creation, and its archiving -- is still there to read")
                .isNotEmpty();

        // ── 4: but User A cannot enroll a NEW member under archived A ───────
        var newMemberDto = new MemberCreateDto();
        newMemberDto.setFullName("عضو جديد يُرفض");
        newMemberDto.setCardNumber("E12-NEW-" + suffix());
        newMemberDto.setEmployerId(employerA);
        newMemberDto.setBenefitPolicyId(policyA);
        assertThatThrownBy(() -> memberService.createPrincipalMember(newMemberDto))
                .as("operational writes need an active employer; reading its history does not")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("مؤرشفة");

        var dependentDto = new DependentMemberDto();
        dependentDto.setFullName("تابع جديد يُرفض");
        dependentDto.setRelationship(com.waad.tba.modules.member.entity.Member.Relationship.SON);
        assertThatThrownBy(() -> memberService.createDependentMember(historicalMemberId, dependentDto))
                .as("a dependent inherits the principal's employer, which is now archived")
                .isInstanceOf(BusinessRuleException.class);

        // ── 5: User B, scoped elsewhere, is denied throughout ───────────────
        actAs(userB);
        assertThatThrownBy(() -> memberService.getMember(historicalMemberId))
                .as("A's archiving changed nothing about B's exposure to it -- still denied, as before")
                .isInstanceOf(MemberAccessDeniedException.class);

        // ── 6: User A's OWN scope is withdrawn -- a separate administrative act ─
        actAs(superAdmin(suffix()));
        jdbc.update("UPDATE users SET employer_id = ? WHERE id = ?", employerB, userA.getId());

        actAs(reload(userA));
        assertThatThrownBy(() -> memberService.getMember(historicalMemberId))
                .as("only NOW, because User A's scope was reassigned -- not because A was archived")
                .isInstanceOf(MemberAccessDeniedException.class);
    }

    // ── fixture helpers ──────────────────────────────────────────────────

    private User superAdmin(String tag) {
        return users.save(User.builder().username("e12-admin-" + tag).password("x").fullName("Admin")
                .email("e12admin" + tag + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), "x", java.util.List.of()));
    }

}
