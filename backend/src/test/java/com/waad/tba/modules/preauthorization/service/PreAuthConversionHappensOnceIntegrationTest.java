package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.service.ClaimApprovalOrchestrator;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * A pre-authorization converts exactly once, and the proof counts CALLS.
 *
 * Counting ledger rows would prove nothing here. Every release the ledger
 * writes carries an idempotency key, so a second conversion adds no row and a
 * table that was written twice looks exactly like one written once. The
 * duplicate would surface only later, as a hold released against a claim that
 * no longer justifies it.
 *
 * The claim posting path is genuinely reachable more than once for one claim:
 * a rejected claim can be re-approved and a soft-deleted one restored, and
 * both re-run the approval commit. So "once" cannot mean "the code path runs
 * once" -- it means the conversion itself happens once, decided by whether a
 * hold is still outstanding rather than by a count of anything.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreAuthConversionHappensOnceIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoSpyBean private PreAuthReservationLedgerService reservationLedger;
    @MockitoSpyBean private PreAuthConversionFinalizer finalizer;

    @Autowired private ClaimService claimService;
    @Autowired private ClaimApprovalOrchestrator orchestrator;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    /** The acting user must exist as a row: @WithMockUser only supplies a principal. */
    @org.junit.jupiter.api.BeforeEach
    void ensureActingUser() {
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Scenario(long preauthId, long memberId, long policyId, long bucketId,
            long visitId, long serviceId) {}

    /**
     * A member, a policy with one bucket, and a submitted pre-authorization
     * for one service -- built the same way the reservation lifecycle tests
     * build theirs, so the approval below runs the production decision path.
     */
    private Scenario scenario(String amountLimit, String requested, int coveragePercent) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('CV-" + s
                + "', 'Convert Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES "
                + "('CVP-" + s + "', 'CVPOL-" + s + "', " + employerId + ", 1000000, " + coveragePercent
                + ", CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id", Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Convert Member', "
                + policyId + ", 'CVC" + s + "', 'CVC" + s + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);

        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('CVCAT-" + s + "', 'Convert Category', true) RETURNING id", Long.class);
        Long serviceId = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, "
                + "cost, active) VALUES ('CVSRV-" + s + "', 'Convert Service', " + categoryId + ", "
                + requested + ", true) RETURNING id", Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', " + coveragePercent
                + ", true, false) RETURNING id", Long.class);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'CVG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, "
                + "code, name_ar, amount_limit, period_type, counting_method, consumption_basis, "
                + "benefit_scope_type, context_type, active) VALUES (" + policyId + ", " + groupId
                + ", 'CVB-" + s + "', 'وعاء', " + amountLimit
                + ", 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)", ruleId, bucketId);

        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type, "
                + "allow_all_employers) VALUES ('CvProv " + s + "', 'CVLIC-" + s
                + "', 'CLINIC', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO provider_accounts (provider_id, running_balance, total_approved, "
                + "total_paid) VALUES (?, 0, 0, 0)", providerId);

        // A contract with priced terms, because the claim path resolves the
        // service's price from it. Without one the claim is refused before the
        // conversion is ever reached, and the test would prove nothing.
        Long contractId = jdbc.queryForObject("INSERT INTO provider_contracts (provider_id, contract_code, "
                + "contract_number, start_date, end_date, discount_percent, "
                + "discount_before_rejection, status, active) VALUES (" + providerId
                + ", 'CVCON-" + s + "', 'CVCNT-" + s
                + "', CURRENT_DATE - 60, CURRENT_DATE + 365, 0, false, 'ACTIVE', true) RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO provider_contract_terms (contract_id, effective_from, discount_percent, "
                + "discount_before_rejection, change_reason) VALUES (?, CURRENT_DATE - 60, 0, false, "
                + "'test initial terms')", contractId);
        jdbc.update("INSERT INTO provider_contract_pricing_items (contract_id, service_code, service_name, "
                + "medical_category_id, base_price, contract_price, effective_from, active) "
                + "VALUES (?, ?, ?, ?, "
                + requested + ", " + requested + ", CURRENT_DATE - 60, true)",
                contractId, "CVSRV-" + s, "Convert Service", categoryId);

        Long visitId = jdbc.queryForObject("INSERT INTO visits (member_id, provider_id, visit_date, status) "
                + "VALUES (" + memberId + ", " + providerId + ", CURRENT_DATE, 'REGISTERED') RETURNING id",
                Long.class);

        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "provider_id, service_category_id, status, request_date, expected_service_date, "
                + "created_at, updated_at, version) VALUES (" + memberId + ", " + policyId + ", "
                + providerId + ", " + categoryId
                + ", 'SUBMITTED', now(), CURRENT_DATE, now(), now(), 0) RETURNING id", Long.class);
        jdbc.update("INSERT INTO pre_authorization_lines (pre_authorization_id, provider_service_id, "
                + "medical_service_id, medical_category_id, provider_service_code, service_name, "
                + "contract_price, requested_amount, coverage_percentage, encounter_type, "
                + "requested_quantity, approved_quantity) VALUES (?, " + serviceId + ", " + serviceId + ", "
                + categoryId + ", ?, ?, " + requested + ", " + requested + ", " + coveragePercent
                + ", 'OUTPATIENT', 1, 1)", preauthId, "SVC-" + s, "Service " + s);

        return new Scenario(preauthId, memberId, policyId, bucketId, visitId, serviceId);
    }

    private String preauthStatus(long preauthId) {
        return jdbc.queryForObject("SELECT status FROM pre_authorizations WHERE id = ?",
                String.class, preauthId);
    }

    private long releaseRows(long preauthId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE preauth_id = ? "
                + "AND status = 'REVERSED' AND reversal_reason = 'PREAUTH_CONVERSION_RELEASE'",
                Long.class, preauthId);
    }

    /**
     * An approval holds more than one thing: the bucket, and the policy's
     * general ceiling. So the count that means "released exactly once" is one
     * compensating movement per hold -- not one movement in total.
     */
    private long holdRows(long preauthId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE preauth_id = ? "
                + "AND status = 'RESERVED'", Long.class, preauthId);
    }

    private BigDecimal netReserved(long memberId, long bucketId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(c.approved_amount - COALESCE(r.released, 0)), 0) "
                        + "FROM benefit_bucket_consumptions c LEFT JOIN ("
                        + "  SELECT reversal_of_id, SUM(approved_amount) AS released "
                        + "  FROM benefit_bucket_consumptions WHERE status='REVERSED' "
                        + "  GROUP BY reversal_of_id) r ON r.reversal_of_id = c.id "
                        + "WHERE c.member_id = ? AND c.bucket_id = ? AND c.status = 'RESERVED'",
                BigDecimal.class, memberId, bucketId);
    }

    private ClaimViewDto claimAgainst(Scenario sc, BigDecimal amount) {
        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(sc.visitId())
                .serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT)
                .preAuthorizationId(sc.preauthId())
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(sc.serviceId()).quantity(1)
                        .requestedTotal(amount).build()))
                .build());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void aClaimConvertsItsApprovalOnceAndLeavesNoHoldBehind() {
        Scenario sc = scenario("1000.00", "300.00", 100);
        reservationLedger.approveAndReserve(sc.preauthId(), 0L, "reviewer");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("300.00");

        ClaimViewDto claim = claimAgainst(sc, new BigDecimal("300.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // The call, not the row count.
        verify(reservationLedger, times(1))
                .releaseOnConversion(eq(sc.preauthId()), eq(claim.getId()), any());

        assertThat(preauthStatus(sc.preauthId())).isEqualTo("USED");
        assertThat(releaseRows(sc.preauthId()))
                .as("one compensating movement per hold, and no more")
                .isEqualTo(holdRows(sc.preauthId()));

        // The hold is gone and the claim's own consumption stands in its place.
        // A conversion that released nothing would leave the member charged
        // twice for one service.
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("0.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void reRunningTheApprovalCommitDoesNotConvertASecondTime() {
        Scenario sc = scenario("1000.00", "300.00", 100);
        reservationLedger.approveAndReserve(sc.preauthId(), 0L, "reviewer");
        ClaimViewDto claim = claimAgainst(sc, new BigDecimal("300.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // Exactly what re-approving a rejected claim, or restoring a
        // soft-deleted one, does: the same commit gate runs again.
        transactionTemplate.executeWithoutResult(
                tx -> orchestrator.commitApprovedClaim(claim.getId(), null));

        // The finalizer ran twice -- it must, it is on the path -- and the
        // conversion still happened once. That distinction is the whole test:
        // an idempotency key would have made the ledger identical either way.
        verify(finalizer, times(2)).finalizeConvertedClaim(eq(claim.getId()), any());
        verify(reservationLedger, times(1)).releaseOnConversion(anyLong(), anyLong(), any());

        assertThat(releaseRows(sc.preauthId()))
                .as("the second run added no movement of its own")
                .isEqualTo(holdRows(sc.preauthId()));
        assertThat(preauthStatus(sc.preauthId())).isEqualTo("USED");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("0.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void aCancelledApprovalCannotBeAttachedToANewClaim() {
        Scenario sc = scenario("1000.00", "300.00", 100);
        reservationLedger.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        // The service really was delivered; the approval was cancelled in
        // between. Cancelling hands the hold back, so by the time the claim
        // arrives there is nothing left to release.
        reservationLedger.cancelAndRelease(sc.preauthId(), "طلب المزود الإلغاء", "reviewer");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("0.00");

        // A delivered service may still be submitted as an ordinary claim, but it
        // must not retain a cancelled approval link and inherit authority that no
        // longer exists. The caller has to remove the stale link explicitly.
        assertThatThrownBy(() -> claimAgainst(sc, new BigDecimal("300.00")))
                .hasMessageContaining("ليست في حالة صالحة");
        verify(reservationLedger, never()).releaseOnConversion(anyLong(), anyLong(), any());
        assertThat(preauthStatus(sc.preauthId())).isEqualTo("CANCELLED");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void cancellingAnApprovalHandsItsHoldBackInsteadOfStrandingIt() {
        Scenario sc = scenario("1000.00", "300.00", 100);
        reservationLedger.approveAndReserve(sc.preauthId(), 0L, "reviewer");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("300.00");

        reservationLedger.cancelAndRelease(sc.preauthId(), "إلغاء إداري", "reviewer");

        // The status and the ledger have to agree. A cancelled approval whose
        // rows stay RESERVED shrinks the member's limit for the rest of the
        // period, and nothing would ever release it: release() refuses a
        // pre-authorization that is already CANCELLED.
        assertThat(preauthStatus(sc.preauthId())).isEqualTo("CANCELLED");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'RESERVED'", Long.class, sc.preauthId()))
                .isEqualTo(holdRows(sc.preauthId()));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void aClaimWithNoApprovalNeverTouchesTheReservationLedger() {
        Scenario sc = scenario("1000.00", "300.00", 100);

        ClaimViewDto claim = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(sc.visitId())
                .serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(sc.serviceId()).quantity(1)
                        .requestedTotal(new BigDecimal("300.00")).build()))
                .build());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // An ordinary claim has no hold to hand back, and reaching the release
        // writer at all would mean the conversion is keyed off something other
        // than the claim's own link.
        verify(reservationLedger, never()).releaseOnConversion(anyLong(), anyLong(), any());
    }
}
