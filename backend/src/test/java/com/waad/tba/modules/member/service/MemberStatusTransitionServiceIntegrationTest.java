package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberHardDeleteAudit;
import com.waad.tba.modules.member.entity.MemberStatusHistory;
import com.waad.tba.modules.member.entity.StatusSource;
import com.waad.tba.modules.member.repository.MemberHardDeleteAuditRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.repository.MemberStatusHistoryRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Real-Postgres proof of the centralized member status lifecycle
 * (MemberStatusTransitionService + V169's DB-level invariant + the
 * append-only member_status_history/member_hard_delete_audit tables).
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberStatusTransitionServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberStatusTransitionService transitionService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberStatusHistoryRepository historyRepository;
    @Autowired private MemberHardDeleteAuditRepository hardDeleteAuditRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Employer newEmployer(String s) {
        return employerRepository.save(Employer.builder()
                .name("Lifecycle Co " + s).code("LC-" + s).active(true).build());
    }

    private BenefitPolicy newPolicy(Employer employer, String s) {
        return benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + s).policyCode("POL-LC-" + s).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
    }

    private Member newPrincipal(Employer employer, BenefitPolicy policy, String s) {
        return memberRepository.save(Member.builder()
                .fullName("Principal " + s).employer(employer).benefitPolicy(policy)
                .cardNumber("CARD" + s).barcode("CARD" + s)
                .status(Member.MemberStatus.ACTIVE).active(true).build());
    }

    private Member newDependent(Member principal, Employer employer, BenefitPolicy policy, String s, String suffix2) {
        return memberRepository.save(Member.builder()
                .fullName("Dependent " + suffix2).employer(employer).benefitPolicy(policy)
                .parent(principal).relationship(Member.Relationship.SON)
                .cardNumber("CARD" + s + "S" + suffix2).barcode("CARD" + s + "S" + suffix2)
                .status(Member.MemberStatus.ACTIVE).active(true).build());
    }

    // 1. Prevent SUSPENDED + active=true (and every other inconsistent pair) at the DB level.
    @Test
    void dbRejectsInconsistentStatusActivePairs() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO members (employer_id, full_name, benefit_policy_id, status, active, card_number, barcode) "
                            + "VALUES (?, ?, ?, 'SUSPENDED', true, ?, ?)")) {
                ps.setLong(1, employer.getId());
                ps.setString(2, "Bad Row " + s);
                ps.setLong(3, policy.getId());
                ps.setString(4, "BADCARD" + s);
                ps.setString(5, "BADCARD" + s);
                assertThatThrownBy(ps::executeUpdate).hasMessageContaining("chk_member_status_active_consistency");
            }
        }
    }

    // 2. toggleActive(true) must not restore a TERMINATED member.
    @Test
    void toggleActiveDoesNotRestoreATerminatedMember() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);

        transitionService.terminateMembership(principal.getId(), "test termination", 1L, StatusSource.MANUAL);

        assertThatThrownBy(() -> transitionService.restoreFromSuspended(principal.getId(), "trying to reactivate", 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("منتهي العضوية");

        Member reloaded = memberRepository.findById(principal.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        assertThat(reloaded.getActive()).isFalse();
    }

    // 3. Suspend then restore: two history records.
    @Test
    void suspendThenRestoreProducesTwoHistoryRecords() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);

        transitionService.suspend(principal.getId(), "إجازة طويلة", 1L);
        transitionService.restoreFromSuspended(principal.getId(), "عاد من الإجازة", 1L);

        List<MemberStatusHistory> history = historyRepository.findByMemberIdOrderByChangedAtDesc(principal.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getToStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(history.get(1).getToStatus()).isEqualTo(Member.MemberStatus.SUSPENDED);

        Member reloaded = memberRepository.findById(principal.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(reloaded.getActive()).isTrue();
    }

    // 4. Terminate then attempt restore without a reason: rejected.
    //
    // The permission half of this used to live here, as a boolean argument the
    // caller passed in. It moved to MemberCommandAccessPolicy against
    // MemberOperation.REINSTATE_TERMINATED, where the rest of the module's
    // access decisions are, and is asserted by
    // MemberCommandAccessPolicyIntegrationTest. What stays is what the state
    // machine actually owns: a reinstatement must say why.
    @Test
    void reinstateTerminatedRejectedWithoutAReason() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        transitionService.terminateMembership(principal.getId(), "إنهاء", 1L, StatusSource.MANUAL);

        assertThatThrownBy(() -> transitionService.reinstateTerminated(principal.getId(), null, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("سبب");

        assertThatThrownBy(() -> transitionService.reinstateTerminated(principal.getId(), "  ", 1L))
                .isInstanceOf(BusinessRuleException.class);

        Member reloaded = memberRepository.findById(principal.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
    }

    // 5. Terminate then restore WITH permission: succeeds and is audited.
    @Test
    void reinstateTerminatedSucceedsWithAReasonAndIsAudited() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        transitionService.terminateMembership(principal.getId(), "إنهاء أولي", 1L, StatusSource.MANUAL);

        Member reinstated = transitionService.reinstateTerminated(principal.getId(),
                "قرار إداري بإعادة القيد", 9L);

        assertThat(reinstated.getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(reinstated.getActive()).isTrue();

        List<MemberStatusHistory> history = historyRepository.findByMemberIdOrderByChangedAtDesc(principal.getId());
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        MemberStatusHistory latest = history.get(0);
        assertThat(latest.getFromStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        assertThat(latest.getToStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(latest.getReason()).contains("إعادة القيد");
        assertThat(latest.getChangedBy()).isEqualTo(9L);
    }

    // 6. Suspending a dependent does not change the rest of the family.
    @Test
    void suspendingADependentDoesNotAffectPrincipalOrSiblings() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Member depA = newDependent(principal, employer, policy, s, "A");
        Member depB = newDependent(principal, employer, policy, s, "B");

        transitionService.suspend(depA.getId(), "سبب خاص بالتابع أ", 1L);

        assertThat(memberRepository.findById(depA.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.SUSPENDED);
        assertThat(memberRepository.findById(depB.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(memberRepository.findById(principal.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
    }

    // 7. Suspending the principal only affects currently-ACTIVE dependents.
    // 8. A dependent independently suspended keeps their own reason -- not overwritten by the cascade.
    @Test
    void suspendingPrincipalCascadesOnlyToActiveDependentsAndKeepsIndependentHistoryIntact() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Member working = newDependent(principal, employer, policy, s, "W");
        Member alreadySuspended = newDependent(principal, employer, policy, s, "P");

        transitionService.suspend(alreadySuspended.getId(), "سبب مستقل خاص بهذا التابع", 1L);
        String independentTransitionId = memberRepository.findById(alreadySuspended.getId()).orElseThrow().getStatusTransitionId();

        transitionService.suspend(principal.getId(), "إيقاف الموظف الرئيسي", 1L);

        Member workingAfter = memberRepository.findById(working.getId()).orElseThrow();
        assertThat(workingAfter.getStatus()).isEqualTo(Member.MemberStatus.SUSPENDED);
        assertThat(workingAfter.getStatusSource()).isEqualTo(StatusSource.FAMILY_CASCADE);
        assertThat(workingAfter.getStatusReason()).contains("إيقاف الموظف الرئيسي");

        Member alreadySuspendedAfter = memberRepository.findById(alreadySuspended.getId()).orElseThrow();
        assertThat(alreadySuspendedAfter.getStatus()).isEqualTo(Member.MemberStatus.SUSPENDED);
        assertThat(alreadySuspendedAfter.getStatusSource()).isEqualTo(StatusSource.MANUAL);
        assertThat(alreadySuspendedAfter.getStatusReason()).isEqualTo("سبب مستقل خاص بهذا التابع");
        assertThat(alreadySuspendedAfter.getStatusTransitionId()).isEqualTo(independentTransitionId);

        // Only one history row for the independently-suspended dependent -- the
        // principal's cascade never touched them.
        assertThat(historyRepository.findByMemberIdOrderByChangedAtDesc(alreadySuspended.getId())).hasSize(1);
    }

    // 9. Restoring the principal does not auto-restore dependents.
    @Test
    void restoringPrincipalDoesNotAutoRestoreDependents() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Member dependent = newDependent(principal, employer, policy, s, "D");

        transitionService.suspend(principal.getId(), "إيقاف الأسرة", 1L);
        assertThat(memberRepository.findById(dependent.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.SUSPENDED);

        transitionService.restoreFromSuspended(principal.getId(), "عودة الرئيسي فقط", 1L);

        assertThat(memberRepository.findById(principal.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(memberRepository.findById(dependent.getId()).orElseThrow().getStatus())
                .as("dependent must stay suspended until explicitly restored")
                .isEqualTo(Member.MemberStatus.SUSPENDED);
    }

    // 10. Optional family restore restores only the members of the specific transitionId.
    @Test
    void restoreFamilyRestoresOnlyTheMembersOfThatSpecificCascade() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Member cascaded = newDependent(principal, employer, policy, s, "C");
        Member independentlySuspended = newDependent(principal, employer, policy, s, "I");

        transitionService.suspend(independentlySuspended.getId(), "سبب مستقل", 1L);
        Member suspendedPrincipal = transitionService.suspend(principal.getId(), "إيقاف الأسرة", 1L);
        String transitionId = suspendedPrincipal.getStatusTransitionId();

        var result = transitionService.restoreFamily(transitionId, 1L);

        assertThat(result.restoredMemberIds()).containsExactly(cascaded.getId());
        assertThat(memberRepository.findById(cascaded.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(memberRepository.findById(independentlySuspended.getId()).orElseThrow().getStatus())
                .as("never touched by a restoreFamily for a different cascade")
                .isEqualTo(Member.MemberStatus.SUSPENDED);
        // The principal itself was not part of the FAMILY_CASCADE set (they're the
        // source of it), so restoreFamily never touches them either.
        assertThat(memberRepository.findById(principal.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.SUSPENDED);
    }

    // 11. Physical delete with no history removes the allowed relations.
    @Test
    void hardDeleteWithNoHistoryRemovesTheMemberAndWritesIndependentAudit() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Long id = principal.getId();

        transitionService.hardDelete(id, "بيانات اختبار مكررة", 5L, "admin", true);

        assertThat(memberRepository.findById(id)).isEmpty();
        List<MemberHardDeleteAudit> audits = hardDeleteAuditRepository.findByMemberId(id);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getReason()).isEqualTo("بيانات اختبار مكررة");
        assertThat(audits.get(0).getPerformedBy()).isEqualTo(5L);
        // There is no transition history in this case. The separate preservation
        // test below proves that existing history survives a physical delete.
    }

    // 12. Physical delete with a financial/medical footprint is forbidden.
    @Test
    void hardDeleteBlockedWhenMemberHasFinancialFootprint() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);

        // Give it a visit -- one of the financial/medical footprint tables.
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Long providerId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO providers (name, provider_type, license_number, allow_all_employers, active) "
                            + "VALUES (?, 'HOSPITAL', ?, true, true) RETURNING id")) {
                ps.setString(1, "Hospital " + s);
                ps.setString(2, "LIC-" + s);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    providerId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO visits (member_id, provider_id, visit_date, status, created_at, updated_at) "
                            + "VALUES (?, ?, CURRENT_DATE, 'REGISTERED', now(), now())")) {
                ps.setLong(1, principal.getId());
                ps.setLong(2, providerId);
                ps.executeUpdate();
            }
        }

        assertThatThrownBy(() -> transitionService.hardDelete(principal.getId(), "محاولة حذف", 1L, "admin", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("زيارات");

        assertThat(memberRepository.findById(principal.getId())).isPresent();
    }

    // 13. Logical termination must remain available when history exists.
    @Test
    void terminateFamilyWithFinancialHistoryPreservesHistoryAndTerminatesMembers() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Member dependent = newDependent(principal, employer, policy, s, "F");

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Long providerId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO providers (name, provider_type, license_number, allow_all_employers, active) "
                            + "VALUES (?, 'HOSPITAL', ?, true, true) RETURNING id")) {
                ps.setString(1, "Hospital2 " + s);
                ps.setString(2, "LIC2-" + s);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    providerId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO visits (member_id, provider_id, visit_date, status, created_at, updated_at) "
                            + "VALUES (?, ?, CURRENT_DATE, 'REGISTERED', now(), now())")) {
                ps.setLong(1, dependent.getId());
                ps.setLong(2, providerId);
                ps.executeUpdate();
            }
        }

        transitionService.terminateMembership(principal.getId(), "انتهاء التغطية", 1L, StatusSource.MANUAL);

        assertThat(memberRepository.findById(principal.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        assertThat(memberRepository.findById(dependent.getId()).orElseThrow().getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM visits WHERE member_id = ?")) {
            ps.setLong(1, dependent.getId());
            try (var rs = ps.executeQuery()) { rs.next(); assertThat(rs.getLong(1)).isEqualTo(1); }
        }
    }

    @Test
    void hardDeleteMemberWithStatusHistoryPreservesImmutableHistory() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Long id = principal.getId();
        String name = principal.getFullName();

        transitionService.suspend(id, "إيقاف تجريبي", 1L);
        transitionService.hardDelete(id, "سجل مكرر بلا أثر مالي", 1L, "admin", true);

        assertThat(memberRepository.findById(id)).isEmpty();
        List<MemberStatusHistory> history = historyRepository.findByMemberIdOrderByChangedAtDesc(id);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getMemberFullName()).isEqualTo(name);
        assertThat(history.get(0).getMemberCardNumber()).isEqualTo(principal.getCardNumber());
    }

    @Test
    void manualTransitionsRejectBlankReasons() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);

        assertThatThrownBy(() -> transitionService.suspend(principal.getId(), " ", 1L))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> transitionService.terminateMembership(principal.getId(), null, 1L, StatusSource.MANUAL))
                .isInstanceOf(BusinessRuleException.class);
        transitionService.suspend(principal.getId(), "إيقاف", 1L);
        assertThatThrownBy(() -> transitionService.restoreFromSuspended(principal.getId(), "", 1L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void hardDeleteAuditRollsBackWhenPhysicalDeleteFails() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Long id = principal.getId();

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = conn.createStatement()) {
            statement.execute("CREATE OR REPLACE FUNCTION fail_member_delete_test() RETURNS trigger AS $$ "
                    + "BEGIN RAISE EXCEPTION 'forced member delete failure'; END; $$ LANGUAGE plpgsql");
            statement.execute("CREATE TRIGGER trg_fail_member_delete_test BEFORE DELETE ON members "
                    + "FOR EACH ROW EXECUTE FUNCTION fail_member_delete_test() ");
        }
        try {
            assertThatThrownBy(() -> transitionService.hardDelete(id, "اختبار التراجع", 1L, "admin", true))
                    .isInstanceOf(Exception.class);
            assertThat(memberRepository.findById(id)).isPresent();
            assertThat(hardDeleteAuditRepository.findByMemberId(id)).isEmpty();
        } finally {
            try (Connection conn = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = conn.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS trg_fail_member_delete_test ON members");
                statement.execute("DROP FUNCTION IF EXISTS fail_member_delete_test()");
            }
        }
    }

    // 14. Two concurrent transitions on the same member: one succeeds, the other gets a conflict.
    @Test
    void concurrentTransitionsOneSucceedsOneGetsOptimisticLockConflict() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        Long id = principal.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<String> suspendTask = () -> {
            startGate.await();
            transitionService.suspend(id, "تعليق من المستخدم أ", 1L);
            return "SUSPEND_OK";
        };
        Callable<String> terminateTask = () -> {
            startGate.await();
            transitionService.terminateMembership(id, "إنهاء من المستخدم ب", 2L, StatusSource.MANUAL);
            return "TERMINATE_OK";
        };

        Future<String> f1 = pool.submit(suspendTask);
        Future<String> f2 = pool.submit(terminateTask);
        startGate.countDown();

        int succeeded = 0;
        int conflicted = 0;
        for (Future<String> f : List.of(f1, f2)) {
            try {
                f.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
                conflicted++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).as("exactly one of the two concurrent transitions should succeed").isEqualTo(1);
        assertThat(conflicted).isEqualTo(1);

        // Whichever won, the member ends up in a single, well-defined state --
        // never a silent overwrite with no trace of the conflict.
        Member finalState = memberRepository.findById(id).orElseThrow();
        assertThat(finalState.getStatus()).isIn(Member.MemberStatus.SUSPENDED, Member.MemberStatus.TERMINATED);
        assertThat(historyRepository.findByMemberIdOrderByChangedAtDesc(id)).hasSize(1);
    }

    // 15. The Excel import row processor does not bypass the transition service:
    // an imported member's status/active/tracking fields end up consistent and
    // attributed to StatusSource.IMPORT.
    @Test
    void applyStatusFieldsForImportProducesTheSameConsistentShapeAsARealTransition() {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member member = Member.builder()
                .fullName("Imported " + s).employer(employer).benefitPolicy(policy)
                .cardNumber("IMP" + s).barcode("IMP" + s).build(); // status defaults ACTIVE

        transitionService.applyStatusFieldsForImport(member, Member.MemberStatus.TERMINATED, "مستورد كمنتهي العضوية");

        assertThat(member.getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        assertThat(member.getActive()).isFalse();
        assertThat(member.getStatusSource()).isEqualTo(StatusSource.IMPORT);
        assertThat(member.getPreviousStatus()).isEqualTo(Member.MemberStatus.ACTIVE);
        assertThat(member.getStatusTransitionId()).isNotBlank();

        // And it actually persists cleanly (satisfies the DB-level invariant).
        Member saved = memberRepository.save(member);
        Member reloaded = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(Member.MemberStatus.TERMINATED);
        assertThat(reloaded.getActive()).isFalse();
    }

    // 16. member_status_history is append-only: UPDATE/DELETE are rejected by Postgres itself.
    @Test
    void memberStatusHistoryIsAppendOnlyAtTheDatabaseLevel() throws Exception {
        String s = suffix();
        Employer employer = newEmployer(s);
        BenefitPolicy policy = newPolicy(employer, s);
        Member principal = newPrincipal(employer, policy, s);
        transitionService.suspend(principal.getId(), "لإثبات عدم القابلية للتعديل", 1L);

        Long historyId = historyRepository.findByMemberIdOrderByChangedAtDesc(principal.getId()).get(0).getId();

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE member_status_history SET reason = 'tampered' WHERE id = ?")) {
                update.setLong(1, historyId);
                assertThatThrownBy(update::executeUpdate).hasMessageContaining("append-only");
            }
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM member_status_history WHERE id = ?")) {
                delete.setLong(1, historyId);
                assertThatThrownBy(delete::executeUpdate).hasMessageContaining("append-only");
            }
        }
    }
}
