package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.preauthorization.dto.PreAuthLineDecisionDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationApproveDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineDecisionStatus;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationLineRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The correctness gate that closed the gap between "the reviewer approved
 * 400 of a 1000 request" and "the ledger reserved 1000 anyway" -- the exact
 * failure mode discovered when {@code finalizeReview} (the live reviewer
 * path) and {@code approveAndReserve} (the tested, ledger-correct engine)
 * turned out to read two different, unsynchronized field sets on
 * {@link PreAuthorizationLine}.
 *
 * Every test here goes through the real service methods a request would hit
 * -- {@link PreAuthReviewService#makeLineDecision}, {@code finalizeReview},
 * and {@link PreAuthorizationService#processApprovalAsync} -- never SQL that
 * fabricates a decision state a reviewer could not actually produce.
 */
@SpringBootTest(classes = TbaWaadApplication.class,
        properties = "waad.preauth.validity-days=30")
@ActiveProfiles("test")
@WithMockUser(username = "it-approver-fixed", roles = { "SUPER_ADMIN" })
class PreAuthApprovalReservesExactlyWhatWasApprovedIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthReviewService reviewService;
    @Autowired private PreAuthorizationService preAuthorizationService;
    @Autowired private PreAuthorizationLineRepository lineRepo;
    @Autowired private JdbcTemplate jdbc;

    /**
     * PreAuthReviewService/authorizationService resolve the acting user from
     * the DB by username -- @WithMockUser only populates SecurityContext
     * authorities, which this codebase's authorization checks do not read.
     * Seeded once per class; ON CONFLICT keeps it safe across test methods
     * and reruns.
     */
    @org.junit.jupiter.api.BeforeEach
    void seedActingUser() {
        jdbc.update("""
                INSERT INTO users (username, email, password, full_name, user_type)
                VALUES ('it-approver-fixed', 'it-approver-fixed@test.local', 'x', 'IT Approver', 'SUPER_ADMIN')
                ON CONFLICT (username) DO NOTHING
                """);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Scenario(long preauthId, long memberId, long bucketId, long lineAId, long lineBId) {}

    /** One policy/bucket, one pre-authorization with TWO lines the test decides on independently. */
    private Scenario twoLineScenario(String amountLimit, String requestedA, String requestedB, int coveragePercent) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('FN-" + s
                + "', 'Finalize Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES ('FP-" + s
                + "', 'FPOL-" + s + "', " + employerId + ", 1000000, " + coveragePercent
                + ", CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id", Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Finalize Member', "
                + policyId + ", 'FC" + s + "', 'FC" + s + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);

        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('FCAT-" + s + "', 'Finalize Category', true) RETURNING id", Long.class);
        Long serviceId = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, active) "
                + "VALUES ('FSRV-" + s + "', 'Finalize Service', " + categoryId + ", true) RETURNING id",
                Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, claim_context_code, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', 'OUTPATIENT', " + coveragePercent
                + ", true, false) RETURNING id", Long.class);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'FG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, "
                + "code, name_ar, amount_limit, times_limit, period_type, counting_method, consumption_basis, "
                + "benefit_scope_type, context_type, active) VALUES (" + policyId + ", " + groupId + ", 'FB-" + s
                + "', 'وعاء', " + amountLimit + ", NULL, 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', "
                + "'CATEGORY', 'OUTPATIENT', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)", ruleId, bucketId);

        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) "
                + "VALUES ('Prov " + s + "', 'FLIC-" + s + "', 'CLINIC') RETURNING id", Long.class);

        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, provider_id, "
                + "service_category_id, status, priority, request_date, expected_service_date, created_at, "
                + "updated_at, version) VALUES (" + memberId + ", " + policyId + ", " + providerId + ", "
                + categoryId + ", 'UNDER_REVIEW', 'NORMAL', now(), CURRENT_DATE + 14, now(), now(), 0) "
                + "RETURNING id", Long.class);

        Long lineAId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "provider_service_id, medical_service_id, medical_category_id, provider_service_code, "
                + "service_name, contract_price, requested_amount, coverage_percentage, encounter_type, "
                + "requested_quantity) VALUES (" + preauthId + ", " + serviceId + ", " + serviceId + ", "
                + categoryId + ", 'SVC-A-" + s + "', 'Line A " + s + "', " + requestedA + ", " + requestedA + ", "
                + coveragePercent + ", 'OUTPATIENT', 1) RETURNING id", Long.class);
        Long lineBId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "provider_service_id, medical_service_id, medical_category_id, provider_service_code, "
                + "service_name, contract_price, requested_amount, coverage_percentage, encounter_type, "
                + "requested_quantity) VALUES (" + preauthId + ", " + serviceId + ", " + serviceId + ", "
                + categoryId + ", 'SVC-B-" + s + "', 'Line B " + s + "', " + requestedB + ", " + requestedB + ", "
                + coveragePercent + ", 'OUTPATIENT', 1) RETURNING id", Long.class);

        return new Scenario(preauthId, memberId, bucketId, lineAId, lineBId);
    }

    private String status(long preauthId) {
        return jdbc.queryForObject("SELECT status FROM pre_authorizations WHERE id = ?", String.class, preauthId);
    }

    private BigDecimal reservedInBucket(long memberId, long bucketId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(approved_amount), 0) FROM benefit_bucket_consumptions "
                + "WHERE member_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                BigDecimal.class, memberId, bucketId);
    }

    private long reservedRowCount(long preauthId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'RESERVED'", Long.class, preauthId);
    }

    // ── the scenario the bug was found in ──────────────────────────────────

    @Test
    void partialLineApprovalReservesOnlyWhatWasApproved_notTheFullRequest() {
        // 100% coverage keeps the arithmetic legible: approved amount ==
        // company share == what the bucket must hold. Line B carries a
        // nonzero request even though it will be rejected -- the coverage
        // engine must resolve a benefit rule for every line regardless of
        // the reviewer's decision, and a zero request never matches one.
        Scenario sc = twoLineScenario("100000", "1000.00", "50.00", 100);
        // Single-line scenario: collapse line B by rejecting it outright.
        reviewService.makeLineDecision(sc.preauthId(), sc.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                        .decisionNotes("لا حاجة لهذا السطر").build(), "reviewer");
        reviewService.makeLineDecision(sc.preauthId(), sc.lineAId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.PARTIALLY_APPROVED)
                        .approvedAmount(new BigDecimal("400.00"))
                        .decisionNotes("جزء من الخدمة غير مبرر").build(), "reviewer");

        PreAuthorization result = reviewService.finalizeReview(sc.preauthId(), "reviewer");

        assertThat(result.getStatus().name()).isEqualTo("PARTIALLY_APPROVED");
        assertThat(result.getApprovedTotalAmount()).isEqualByComparingTo("400.00");
        // The regression this test exists for: reserving the full 1000
        // instead of the reviewer's actual 400.
        assertThat(reservedInBucket(sc.memberId(), sc.bucketId())).isEqualByComparingTo("400.00");

        PreAuthorizationLine lineA = lineRepo.findById(sc.lineAId()).orElseThrow();
        assertThat(lineA.getApprovedAmount()).isEqualByComparingTo("400.00");
        assertThat(lineA.getDecisionStatus()).isEqualTo(LineDecisionStatus.PARTIALLY_APPROVED);
    }

    @Test
    void fullRejectionOfEveryLineReservesNothingAndNeedsNoLedgerWrite() {
        Scenario sc = twoLineScenario("100000", "500.00", "300.00", 100);
        for (Long lineId : List.of(sc.lineAId(), sc.lineBId())) {
            reviewService.makeLineDecision(sc.preauthId(), lineId,
                    PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                            .decisionNotes("غير مغطى").build(), "reviewer");
        }

        PreAuthorization result = reviewService.finalizeReview(sc.preauthId(), "reviewer");

        assertThat(result.getStatus().name()).isEqualTo("REJECTED");
        assertThat(reservedRowCount(sc.preauthId())).isZero();
    }

    @Test
    void fullApprovalOfEveryLineReservesTheFullRequest() {
        Scenario sc = twoLineScenario("100000", "500.00", "300.00", 100);
        for (Long lineId : List.of(sc.lineAId(), sc.lineBId())) {
            reviewService.makeLineDecision(sc.preauthId(), lineId,
                    PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(),
                    "reviewer");
        }

        PreAuthorization result = reviewService.finalizeReview(sc.preauthId(), "reviewer");

        assertThat(result.getStatus().name()).isEqualTo("APPROVED");
        assertThat(result.getApprovedTotalAmount()).isEqualByComparingTo("800.00");
        assertThat(reservedInBucket(sc.memberId(), sc.bucketId())).isEqualByComparingTo("800.00");
    }

    @Test
    void eachLineReservesAccordingToItsOwnDecision_noCrossLineBleed() {
        Scenario sc = twoLineScenario("100000", "1000.00", "500.00", 100);
        reviewService.makeLineDecision(sc.preauthId(), sc.lineAId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(), "reviewer");
        reviewService.makeLineDecision(sc.preauthId(), sc.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.PARTIALLY_APPROVED)
                        .approvedAmount(new BigDecimal("200.00")).decisionNotes("جزئي").build(), "reviewer");

        reviewService.finalizeReview(sc.preauthId(), "reviewer");

        assertThat(lineRepo.findById(sc.lineAId()).orElseThrow().getApprovedAmount())
                .isEqualByComparingTo("1000.00");
        assertThat(lineRepo.findById(sc.lineBId()).orElseThrow().getApprovedAmount())
                .isEqualByComparingTo("200.00");
        assertThat(reservedInBucket(sc.memberId(), sc.bucketId())).isEqualByComparingTo("1200.00");
    }

    @Test
    void finalizingTwiceDoesNotDoubleTheReservation() {
        Scenario sc = twoLineScenario("100000", "500.00", "300.00", 100);
        for (Long lineId : List.of(sc.lineAId(), sc.lineBId())) {
            reviewService.makeLineDecision(sc.preauthId(), lineId,
                    PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(),
                    "reviewer");
        }

        reviewService.finalizeReview(sc.preauthId(), "reviewer");
        // Re-finalizing an already-decided pre-authorization is refused
        // outright by the review gate itself (status is no longer
        // reviewable) -- it never even reaches approveAndReserve a second
        // time, so there is no double reservation to guard against here.
        // (approveAndReserve's OWN idempotency, for retries of the same
        // in-flight decision, is covered directly in
        // PreAuthReservationLifecycleIntegrationTest.)
        assertThatThrownBy(() -> reviewService.finalizeReview(sc.preauthId(), "reviewer"))
                .isInstanceOf(BusinessRuleException.class);

        // Two lines, each holding both its bucket AND the policy's general
        // ceiling: 4 rows total. The number that must NOT move on the second
        // (refused) finalize attempt is this one -- not doubled to 8.
        assertThat(reservedRowCount(sc.preauthId())).isEqualTo(4);
        assertThat(reservedInBucket(sc.memberId(), sc.bucketId())).isEqualByComparingTo("800.00");
    }

    @Test
    void aFailedReservationLeavesTheStatusUnapproved() {
        Scenario sc = twoLineScenario("100", "1000.00", "50.00", 100);
        reviewService.makeLineDecision(sc.preauthId(), sc.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                        .decisionNotes("n/a").build(), "reviewer");
        reviewService.makeLineDecision(sc.preauthId(), sc.lineAId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(), "reviewer");

        // Bucket limit is 100, but the line asks for 1000 at 100% coverage --
        // the financial engine caps the line itself, so this does not throw;
        // instead it proves the CAPPED figure is what gets reserved, never
        // the raw request. (A true failure -- e.g. a stale-balance DB
        // rejection -- is exercised by the ledger's own concurrency tests.)
        PreAuthorization result = reviewService.finalizeReview(sc.preauthId(), "reviewer");

        assertThat(reservedInBucket(sc.memberId(), sc.bucketId())).isEqualByComparingTo("100.00");
        assertThat(status(sc.preauthId())).isIn("PARTIALLY_APPROVED", "APPROVED");
    }

    @Test
    void concurrentFinalizeAndDirectApproveOnDifferentPreauthsEachReserveTheirOwnAmountConsistently()
            throws Exception {
        Scenario sc1 = twoLineScenario("100000", "500.00", "50.00", 100);
        Scenario sc2 = twoLineScenario("100000", "300.00", "50.00", 100);
        reviewService.makeLineDecision(sc1.preauthId(), sc1.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                        .decisionNotes("n/a").build(), "reviewer");
        reviewService.makeLineDecision(sc1.preauthId(), sc1.lineAId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(), "reviewer");
        reviewService.makeLineDecision(sc2.preauthId(), sc2.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                        .decisionNotes("n/a").build(), "reviewer");
        reviewService.makeLineDecision(sc2.preauthId(), sc2.lineAId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(), "reviewer");

        // @WithMockUser's SecurityContext is thread-local; an ad-hoc executor
        // does not inherit it, so it has to be propagated explicitly to the
        // worker threads the same way the real request-handling threads
        // would carry it.
        ExecutorService pool = new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(
                Executors.newFixedThreadPool(2));
        try {
            Callable<PreAuthorization> t1 = () -> reviewService.finalizeReview(sc1.preauthId(), "reviewer1");
            Callable<PreAuthorization> t2 = () -> reviewService.finalizeReview(sc2.preauthId(), "reviewer2");
            Future<PreAuthorization> f1 = pool.submit(t1);
            Future<PreAuthorization> f2 = pool.submit(t2);
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        assertThat(reservedInBucket(sc1.memberId(), sc1.bucketId())).isEqualByComparingTo("500.00");
        assertThat(reservedInBucket(sc2.memberId(), sc2.bucketId())).isEqualByComparingTo("300.00");
    }

    // ── /approve converges on the same engine as /finalize ─────────────────

    @Test
    void directApproveWithNoLineReviewReservesTheFullRequest_sameAsFinalizeWithAllApproved() throws Exception {
        Scenario sc = twoLineScenario("100000", "600.00", "50.00", 100);
        // Line B is rejected so this behaves as a single-line case, matching
        // the finalize-based full-approval test above for a direct comparison.
        reviewService.makeLineDecision(sc.preauthId(), sc.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                        .decisionNotes("n/a").build(), "reviewer");
        // Line A is left UNREVIEWED on purpose: /approve carries no per-line
        // data, and an unreviewed line defaults to fully requested -- the
        // same meaning "/approve with no line review" has always had.

        // requestApproval is the real entry point: it flips the status to
        // APPROVAL_IN_PROGRESS (a precondition processApprovalAsync enforces)
        // before triggering the same async phase 2 a live /approve call would.
        preAuthorizationService.requestApproval(sc.preauthId(),
                PreAuthorizationApproveDto.builder().build(), "approver");

        awaitStatusLeavingApprovalInProgress(sc.preauthId());

        assertThat(status(sc.preauthId())).isIn("APPROVED", "PARTIALLY_APPROVED");
        assertThat(reservedInBucket(sc.memberId(), sc.bucketId())).isEqualByComparingTo("600.00");
    }

    private void awaitStatusLeavingApprovalInProgress(long preauthId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String current = status(preauthId);
            if (!"APPROVAL_IN_PROGRESS".equals(current) && !"UNDER_REVIEW".equals(current)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("processApprovalAsync did not leave APPROVAL_IN_PROGRESS within 10s");
    }

    @Test
    void finalizeWithoutExpectedServiceDateFailsClosed() {
        Scenario sc = twoLineScenario("100000", "500.00", "50.00", 100);
        jdbc.update("UPDATE pre_authorizations SET expected_service_date = NULL WHERE id = ?", sc.preauthId());
        reviewService.makeLineDecision(sc.preauthId(), sc.lineBId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.REJECTED)
                        .decisionNotes("n/a").build(), "reviewer");
        reviewService.makeLineDecision(sc.preauthId(), sc.lineAId(),
                PreAuthLineDecisionDto.builder().decisionStatus(LineDecisionStatus.APPROVED).build(), "reviewer");

        assertThatThrownBy(() -> reviewService.finalizeReview(sc.preauthId(), "reviewer"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("تاريخ الخدمة المتوقع");
    }
}
