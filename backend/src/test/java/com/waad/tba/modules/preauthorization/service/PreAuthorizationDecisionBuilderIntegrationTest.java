package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The decision builder, checked against the financial constitution's own
 * worked numbers (S37) and against the rules that make a decision safe to
 * persist later.
 *
 * The property under test throughout is that this component DECIDES and does
 * not ACT: after every case below, the ledger and the pre-authorization's
 * status must be exactly as they were. That is what allows the approval
 * service to run it once for a preview and again under locks, and to discard
 * either result without cleanup.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreAuthorizationDecisionBuilderIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthorizationDecisionBuilder builder;
    @Autowired private PreAuthorizationRepository preauthRepository;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Scenario(long preauthId, long memberId, long policyId, long bucketId) {}

    /**
     * A member with one annual bucket, a contracted provider, and a
     * pre-authorization for one line.
     *
     * @param bucketLimit    the bucket's amount limit
     * @param alreadyCommitted consumption already posted against it
     * @param discountPercent the contract's discount, or null for no contract
     */
    private Scenario scenario(String bucketLimit, String alreadyCommitted, String requested,
            int coveragePercent, String discountPercent, LocalDate serviceDate) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('DB-" + s
                + "', 'Decision Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES ('DP-" + s
                + "', 'DPOL-" + s + "', " + employerId + ", 1000000, " + coveragePercent
                + ", CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id", Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Decision Member', "
                + policyId + ", 'DC" + s + "', 'DC" + s + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);

        // A rule is what maps a service to its buckets; without one no limit
        // applies at all, so the fixture mirrors the production shape:
        // category -> service -> rule -> rule_bucket.
        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('DCAT-" + s + "', 'Decision Category', true) RETURNING id", Long.class);
        Long serviceId = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, active) "
                + "VALUES ('DSRV-" + s + "', 'Decision Service', " + categoryId + ", true) RETURNING id",
                Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', " + coveragePercent
                + ", true, false) RETURNING id", Long.class);

        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'DG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, "
                + "code, name_ar, amount_limit, period_type, counting_method, consumption_basis, "
                + "benefit_scope_type, context_type, active) VALUES ("
                + policyId + ", " + groupId + ", 'DB-" + s + "', 'وعاء', " + bucketLimit
                + ", 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)", ruleId, bucketId);

        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) "
                + "VALUES ('Prov " + s + "', 'DLIC-" + s + "', 'CLINIC') RETURNING id", Long.class);

        Long contractId = null;
        if (discountPercent != null) {
            contractId = jdbc.queryForObject("INSERT INTO provider_contracts (provider_id, contract_code, contract_number, "
                    + "start_date, end_date, status) VALUES (" + providerId + ", 'CC-" + s + "', 'CN-" + s
                    + "', CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE') RETURNING id", Long.class);
            jdbc.update("INSERT INTO provider_contract_terms (contract_id, effective_from, discount_percent, "
                    + "discount_before_rejection) VALUES (?, CURRENT_DATE - 60, " + discountPercent + ", false)",
                    contractId);
        }

        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, provider_id, "
                + "contract_id, service_category_id, status, request_date, expected_service_date, created_at, "
                + "updated_at) VALUES (" + memberId + ", " + policyId + ", " + providerId + ", "
                + (contractId == null ? "NULL" : contractId) + ", " + categoryId + ", 'SUBMITTED', now(), "
                + (serviceDate == null ? "NULL" : "DATE '" + serviceDate + "'")
                + ", now(), now()) RETURNING id", Long.class);
        jdbc.update("INSERT INTO pre_authorization_lines (pre_authorization_id, provider_service_id, "
                + "medical_service_id, medical_category_id, provider_service_code, service_name, "
                + "contract_price, requested_amount, coverage_percentage, encounter_type) VALUES (?, "
                + serviceId + ", " + serviceId + ", " + categoryId + ", ?, ?, "
                + requested + ", " + requested + ", " + coveragePercent + ", 'OUTPATIENT')",
                preauthId, "SVC-" + s, "Service " + s);

        if (alreadyCommitted != null) {
            jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, "
                    + "period_start, period_end, approved_amount, times_consumed, calculation_version, "
                    + "idempotency_key, status, source_type, limit_scope, created_at) VALUES (?, ?, ?, "
                    + "DATE_TRUNC('year', CURRENT_DATE)::date, "
                    + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, "
                    + alreadyCommitted + ", 1, 1, ?, 'COMMITTED', 'OPENING_IMPORT', 'BUCKET', now())",
                    policyId, memberId, bucketId, "OPEN-" + s);
        }

        return new Scenario(preauthId, memberId, policyId, bucketId);
    }

    private PreAuthorization load(long preauthId) {
        return preauthRepository.findById(preauthId).orElseThrow();
    }

    private long ledgerRowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions", Long.class);
    }

    // ── S37 "Full limit": the service fits entirely ─────────────────────

    @Test
    void aRequestThatFitsWithinTheLimitIsApprovedInFull() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, null, LocalDate.now().plusDays(14));

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);

        assertThat(decision.outcome()).isEqualTo(PreAuthorizationDecision.Outcome.APPROVED);
        // S37: 80% coverage of 1000 -> patient 200, insurer 800.
        assertThat(decision.companyShareTotal()).isEqualByComparingTo("800.00");
        assertThat(decision.patientShareTotal()).isEqualByComparingTo("200.00");
        assertThat(decision.settlementTotal()).isEqualByComparingTo("1000.00");
        assertThat(decision.coverageOutcome())
                .isEqualTo(PreAuthorizationDecision.CoverageOutcome.FULLY_COVERED);
    }

    // ── S37 "Provider discount": applies to the insurer's share only ────

    @Test
    void theProviderDiscountReducesTheInsurerShareAndNotThePatients() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, "10.00", LocalDate.now().plusDays(14));

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);

        // S37: insurer gross 800, discount 80, final 720. The patient still
        // owes 200 -- the patient benefits from the contract price, never from
        // the insurer's additional discount.
        assertThat(decision.companyShareTotal()).isEqualByComparingTo("720.00");
        assertThat(decision.patientShareTotal()).isEqualByComparingTo("200.00");
    }

    // ── S37 "Partial remaining limit": the excess is the patient's ──────

    @Test
    void aRequestExceedingTheRemainingLimitIsApprovedForThePartThatFits() {
        // Bucket of 1000 with 800 already consumed leaves 200 reservable.
        Scenario sc = scenario("1000", "800.00", "400.00", 80, null, LocalDate.now().plusDays(14));

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);

        // S37: inside limit 200 -> patient coverage share 40, limit excess
        // 200, patient total 240, insurer 160.
        assertThat(decision.companyShareTotal()).isEqualByComparingTo("160.00");
        assertThat(decision.patientShareTotal()).isEqualByComparingTo("240.00");

        // The SERVICE was authorised in full -- nothing about it was refused.
        // Reporting this as "partially approved" would tell the patient their
        // request was cut down, when what actually happened is that a limit
        // they had already spent decides who pays.
        assertThat(decision.outcome()).isEqualTo(PreAuthorizationDecision.Outcome.APPROVED);
        assertThat(decision.coverageOutcome())
                .isEqualTo(PreAuthorizationDecision.CoverageOutcome.LIMIT_CAPPED);
        assertThat(decision.limitCapped()).isTrue();
        assertThat(decision.limitExcessTotal()).isEqualByComparingTo("200.00");
        assertThat(decision.authorizedServiceTotal()).isEqualByComparingTo("400.00");
    }

    // ── the reservation rule ────────────────────────────────────────────

    @Test
    void oneAmountIsHeldPerScopeAndTheScopesAreNeverSummed() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, null, LocalDate.now().plusDays(14));

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);
        List<PreAuthorizationDecision.LimitHold> holds = decision.lines().get(0).limitHolds();

        // Several scopes apply (the bucket, and the policy's general ceiling).
        assertThat(holds).hasSizeGreaterThan(1);

        // One decision, measured independently by each scope. Both happen to
        // use COMPANY_SHARE here, so both read 800 -- but they are never
        // ADDED: summing them would reserve 1600 for an 800 decision, the
        // double count the ledger's scope split exists to prevent.
        assertThat(holds).allSatisfy(hold -> {
            assertThat(hold.reservedUnit()).isEqualTo(PreAuthorizationDecision.ReservedUnit.CURRENCY);
            assertThat(hold.amountReserved()).isEqualByComparingTo("800.00");
            assertThat(hold.timesReserved()).isNull();
            assertThat(hold.daysReserved()).isNull();
        });

        assertThat(holds).anySatisfy(hold ->
                assertThat(hold.limitScope()).isEqualTo("POLICY_GENERAL"));
        assertThat(holds).anySatisfy(hold ->
                assertThat(hold.limitScope()).isEqualTo("BUCKET"));
    }

    @Test
    void everyHoldRecordsBothRemainingFiguresAndTheyDiffer() {
        Scenario sc = scenario("1000", "800.00", "100.00", 80, null, LocalDate.now().plusDays(14));

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);
        var bucketHold = decision.lines().get(0).limitHolds().stream()
                .filter(h -> "BUCKET".equals(h.limitScope())).findFirst().orElseThrow();

        // 1000 limit, 800 committed -> 200 actually remaining, and with no
        // existing holds the same 200 is reservable. Recording only one of the
        // two would make the decision unreproducible.
        assertThat(bucketHold.effectiveLimit()).isEqualByComparingTo("1000.00");
        assertThat(bucketHold.committedBefore()).isEqualByComparingTo("800.00");
        assertThat(bucketHold.actualRemainingBefore()).isEqualByComparingTo("200.00");
        assertThat(bucketHold.reservableAvailableBefore()).isEqualByComparingTo("200.00");
    }

    @Test
    void anExistingHoldReducesWhatANewDecisionMayTake() {
        Scenario sc = scenario("1000", null, "1000.00", 100, null, LocalDate.now().plusDays(14));

        // Another approval already holds 600 against the same bucket.
        Long preauthTwo = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, status, "
                + "request_date, created_at, updated_at) VALUES (" + sc.memberId()
                + ", 'APPROVED', now(), now(), now()) RETURNING id", Long.class);
        Long lineTwo = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthTwo + ", 600.00) RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, created_at) VALUES ("
                + "?, ?, ?, ?, ?, DATE_TRUNC('year', CURRENT_DATE)::date, "
                + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, "
                + "600.00, 0, 1, ?, 'RESERVED', 'PREAUTH', 'BUCKET', now())",
                sc.policyId(), sc.memberId(), sc.bucketId(), preauthTwo, lineTwo, "HOLD-" + suffix());

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);
        var bucketHold = decision.lines().get(0).limitHolds().stream()
                .filter(h -> "BUCKET".equals(h.limitScope())).findFirst().orElseThrow();

        // Nothing is CONSUMED, so the member's actual remaining is still the
        // full 1000 -- but only 400 may be promised to a new decision.
        assertThat(bucketHold.actualRemainingBefore()).isEqualByComparingTo("1000.00");
        assertThat(bucketHold.reservedBefore()).isEqualByComparingTo("600.00");
        assertThat(bucketHold.reservableAvailableBefore()).isEqualByComparingTo("400.00");

        // And the decision is capped by the reservable figure, not the actual
        // one. Deciding against 1000 here is how the same limit gets promised
        // twice.
        assertThat(decision.companyShareTotal()).isEqualByComparingTo("400.00");
    }

    // ── fail closed ─────────────────────────────────────────────────────

    @Test
    void aPreAuthorizationWithoutAnExpectedServiceDateIsRefused() {
        Scenario sc = scenario("1000000", null, "500.00", 80, null, null);

        // Substituting today would resolve a FUTURE service against today's
        // policy and today's contract terms.
        assertThatThrownBy(() -> builder.build(sc.preauthId(), 1))
                .hasMessageContaining("تاريخ الخدمة المتوقع");
    }

    @Test
    void aContractedProviderWithoutEffectiveTermsIsRefused() {
        // Terms effective from 60 days ago, but the service is expected in two
        // years -- outside any terms row.
        Scenario sc = scenario("1000000", null, "500.00", 80, "10.00", LocalDate.now().plusDays(14));
        jdbc.update("UPDATE provider_contract_terms SET effective_to = CURRENT_DATE - 1 "
                + "WHERE contract_id = (SELECT contract_id FROM pre_authorizations WHERE id = ?)",
                sc.preauthId());

        assertThatThrownBy(() -> builder.build(sc.preauthId(), 1))
                .hasMessageContaining("شروط عقد");
    }

    @Test
    void anExhaustedLimitCapsTheCoverageWithoutRefusingTheService() {
        Scenario sc = scenario("1000", "1000.00", "500.00", 80, null, LocalDate.now().plusDays(14));

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);

        // The service is still authorised; the insurer simply covers none of
        // it, and the whole value falls to the patient as limit excess.
        assertThat(decision.outcome()).isEqualTo(PreAuthorizationDecision.Outcome.APPROVED);
        assertThat(decision.coverageOutcome())
                // Exhausted, not merely capped: the insurer pays nothing at
                // all. And NOT "0% coverage" -- the policy still covers 80%;
                // what reached zero is the payable amount.
                .isEqualTo(PreAuthorizationDecision.CoverageOutcome.LIMIT_EXHAUSTED);
        assertThat(decision.companyShareTotal()).isEqualByComparingTo("0.00");
        assertThat(decision.patientShareTotal()).isEqualByComparingTo("500.00");
        assertThat(decision.lines().get(0).coveragePercent())
                .as("the policy percentage is unchanged by an exhausted ceiling").isEqualTo(80);
        assertThat(decision.lines().get(0).companyShareBeforeLimit())
                .as("what the policy would have paid").isEqualByComparingTo("400.00");
    }

    // ── the whole point: it decides, it does not act ────────────────────

    @Test
    void buildingADecisionWritesNothingAtAll() {
        Scenario sc = scenario("1000", "200.00", "400.00", 80, "10.00", LocalDate.now().plusDays(14));
        long ledgerBefore = ledgerRowCount();
        String statusBefore = jdbc.queryForObject(
                "SELECT status FROM pre_authorizations WHERE id = ?", String.class, sc.preauthId());

        builder.build(sc.preauthId(), 1);
        builder.build(sc.preauthId(), 1); // and again -- still nothing

        assertThat(ledgerRowCount()).as("no hold may be created by a decision").isEqualTo(ledgerBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preauth_decision_snapshots WHERE preauth_id = ?",
                Long.class, sc.preauthId())).as("no snapshot may be written").isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM pre_authorizations WHERE id = ?",
                String.class, sc.preauthId())).as("the status must be untouched").isEqualTo(statusBefore);
    }

    @Test
    void theSameInputProducesTheSameDecisionEveryTime() {
        Scenario sc = scenario("1000", "200.00", "400.00", 80, "10.00", LocalDate.now().plusDays(14));

        PreAuthorizationDecision first = builder.build(sc.preauthId(), 1);
        PreAuthorizationDecision second = builder.build(sc.preauthId(), 1);

        assertThat(second.companyShareTotal()).isEqualByComparingTo(first.companyShareTotal());
        assertThat(second.patientShareTotal()).isEqualByComparingTo(first.patientShareTotal());
        assertThat(second.outcome()).isEqualTo(first.outcome());
    }

    // ── the basis is dated, not current ─────────────────────────────────

    @Test
    void theBasisRecordsWhatWasResolvedOnTheExpectedServiceDate() {
        LocalDate serviceDate = LocalDate.now().plusDays(30);
        Scenario sc = scenario("1000000", null, "500.00", 80, "15.00", serviceDate);

        PreAuthorizationDecision.Basis basis = builder.build(sc.preauthId(), 1).basis();

        assertThat(basis.expectedServiceDate()).isEqualTo(serviceDate);
        assertThat(basis.policyId()).isEqualTo(sc.policyId());
        assertThat(basis.memberPolicyAssignmentId()).as("the coverage period, not just the policy").isNotNull();
        assertThat(basis.contractTermsId()).as("the exact terms row that priced this").isNotNull();
        assertThat(basis.discountPercent()).isEqualByComparingTo("15.00");
        assertThat(basis.discountBeforeRejection()).isNotNull();
        assertThat(basis.policyVersion()).as("makes later drift detectable").isNotNull();
    }

    // ── the mandatory cases ─────────────────────────────────────────────

    @Test
    void aLineWithoutAMedicalClassificationFailsClosed() {
        Scenario sc = scenario("1000000", null, "500.00", 80, null, LocalDate.now().plusDays(14));
        jdbc.update("UPDATE pre_authorization_lines SET medical_category_id = NULL "
                + "WHERE pre_authorization_id = ?", sc.preauthId());

        // No classification means no rule, which the limit resolver would
        // report as no ceiling at all -- approving against nothing.
        assertThatThrownBy(() -> builder.build(sc.preauthId(), 1))
                .hasMessageContaining("تصنيف طبي");
    }

    @Test
    void discountBeforeAndAfterRejectionProduceDifferentNumbers() {
        // 1000 requested, 80% coverage, 200 explicitly refused, 10% discount.
        // The discount's position relative to the refusal changes what the
        // insurer finally pays -- which is the whole reason the ORDER is part
        // of the recorded basis rather than a detail of the contract.
        Scenario after = scenario("1000000", null, "1000.00", 80, "10.00", LocalDate.now().plusDays(14));
        refuse(after, "200.00");
        PreAuthorizationDecision afterDecision = builder.build(after.preauthId(), 1);

        Scenario before = scenario("1000000", null, "1000.00", 80, "10.00", LocalDate.now().plusDays(14));
        refuse(before, "200.00");
        jdbc.update("UPDATE provider_contract_terms SET discount_before_rejection = true "
                + "WHERE contract_id = (SELECT contract_id FROM pre_authorizations WHERE id = ?)",
                before.preauthId());
        PreAuthorizationDecision beforeDecision = builder.build(before.preauthId(), 1);

        assertThat(afterDecision.basis().discountBeforeRejection()).isFalse();
        assertThat(beforeDecision.basis().discountBeforeRejection()).isTrue();

        // After-rejection: the refusal comes off first, then 10% of what is
        // left. Before-rejection: 10% comes off the gross share first, then
        // the refusal. Different discount bases, different discounts,
        // different final payment.
        assertThat(beforeDecision.providerDiscountTotal())
                .as("the two orders must not produce the same discount")
                .isNotEqualByComparingTo(afterDecision.providerDiscountTotal());
        assertThat(beforeDecision.companyShareTotal())
                .as("nor the same final payment")
                .isNotEqualByComparingTo(afterDecision.companyShareTotal());

        // Both are PARTIALLY_APPROVED: a reviewer refused part of the service.
        assertThat(afterDecision.outcome())
                .isEqualTo(PreAuthorizationDecision.Outcome.PARTIALLY_APPROVED);
        assertThat(afterDecision.rejectedTotal()).isEqualByComparingTo("200.00");
    }

    /** Records a reviewer's explicit partial refusal against the scenario's line. */
    private void refuse(Scenario sc, String amount) {
        jdbc.update("UPDATE pre_authorization_lines SET explicit_rejected_amount = " + amount
                + ", review_decision = 'PARTIALLY_APPROVE', rejection_reason = 'مبالغة في السعر المرجعي' "
                + "WHERE pre_authorization_id = ?", sc.preauthId());
    }

    @Test
    void anEligibleAmountBucketAndTheGeneralCeilingRecordDifferentMeasures() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, null, LocalDate.now().plusDays(14));
        // This bucket counts the ELIGIBLE amount, not the insurer's share.
        jdbc.update("UPDATE benefit_limit_buckets SET consumption_basis = 'ELIGIBLE_AMOUNT' WHERE id = ?",
                sc.bucketId());

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);
        List<PreAuthorizationDecision.LimitHold> holds = decision.lines().get(0).limitHolds();

        var bucket = holds.stream().filter(h -> "BUCKET".equals(h.limitScope())).findFirst().orElseThrow();
        var general = holds.stream().filter(h -> "POLICY_GENERAL".equals(h.limitScope())).findFirst().orElseThrow();

        // The bucket holds the eligible amount (1000), the general ceiling
        // holds the insurer's share (800). Different numbers for the same
        // decision, and neither is wrong -- they measure different things.
        assertThat(bucket.consumptionBasis()).isEqualTo("ELIGIBLE_AMOUNT");
        assertThat(bucket.amountReserved()).isEqualByComparingTo("1000.00");
        assertThat(general.consumptionBasis()).isEqualTo("COMPANY_SHARE");
        assertThat(general.amountReserved()).isEqualByComparingTo("800.00");

        assertThat(bucket.amountReserved()).isNotEqualByComparingTo(general.amountReserved());
    }

    @Test
    void refusingHalfTheQuantityMakesItAPartialApproval() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, null, LocalDate.now().plusDays(14));
        jdbc.update("UPDATE pre_authorization_lines SET requested_quantity = 4, approved_quantity = 2, "
                + "review_decision = 'PARTIALLY_APPROVE', rejection_reason = 'الكمية تتجاوز المبرر الطبي' "
                + "WHERE pre_authorization_id = ?", sc.preauthId());

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);

        // The SERVICE was cut down -- by a reviewer, not by a ceiling.
        assertThat(decision.outcome()).isEqualTo(PreAuthorizationDecision.Outcome.PARTIALLY_APPROVED);
        assertThat(decision.lines().get(0).requestedQuantity()).isEqualTo(4);
        assertThat(decision.lines().get(0).approvedQuantity()).isEqualTo(2);
        assertThat(decision.rejectedTotal()).isEqualByComparingTo("500.00");
        assertThat(decision.authorizedServiceTotal()).isEqualByComparingTo("500.00");
        assertThat(decision.limitCapped()).as("no ceiling was involved").isFalse();
    }

    @Test
    void aRefusalWithoutAStatedReasonIsRefused() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, null, LocalDate.now().plusDays(14));
        jdbc.update("UPDATE pre_authorization_lines SET explicit_rejected_amount = 100.00 "
                + "WHERE pre_authorization_id = ?", sc.preauthId());

        // A refusal nobody explained cannot be appealed or answered.
        assertThatThrownBy(() -> builder.build(sc.preauthId(), 1))
                .hasMessageContaining("سبباً صريحاً");
    }

    @Test
    void twoLinesFromDifferentCategoriesResolveTwoDifferentRules() {
        Scenario sc = scenario("1000000", null, "1000.00", 80, null, LocalDate.now().plusDays(14));
        String s2 = suffix();

        // A second category, with its own rule and its own bucket.
        Long otherCategory = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('DCAT2-" + s2 + "', 'Other Category', true) RETURNING id", Long.class);
        Long otherService = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, active) "
                + "VALUES ('DSRV2-" + s2 + "', 'Other Service', " + otherCategory + ", true) RETURNING id",
                Long.class);
        Long otherRule = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, coverage_percent, active, deleted) VALUES ("
                + sc.policyId() + ", " + otherCategory + ", 'OUTPATIENT', 50, true, false) RETURNING id",
                Long.class);
        Long otherGroup = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + sc.policyId() + ", 'DG2-" + s2
                + "', 'مجموعة أخرى', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long otherBucket = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, "
                + "benefit_group_id, code, name_ar, amount_limit, period_type, counting_method, "
                + "consumption_basis, benefit_scope_type, context_type, active) VALUES (" + sc.policyId()
                + ", " + otherGroup + ", 'DB2-" + s2 + "', 'وعاء آخر', 999999, 'ANNUAL', 'EACH_LINE', "
                + "'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)",
                otherRule, otherBucket);

        jdbc.update("INSERT INTO pre_authorization_lines (pre_authorization_id, provider_service_id, "
                + "medical_service_id, medical_category_id, provider_service_code, service_name, "
                + "contract_price, requested_amount, coverage_percentage, encounter_type) VALUES (?, "
                + otherService + ", " + otherService + ", " + otherCategory + ", ?, ?, "
                + "1000.00, 1000.00, 50, 'OUTPATIENT')", sc.preauthId(), "SVC2-" + s2, "Other " + s2);

        PreAuthorizationDecision decision = builder.build(sc.preauthId(), 1);

        assertThat(decision.lines()).hasSize(2);

        // Each line resolved its OWN rule from its OWN category. Resolving
        // from the pre-authorization head would have priced both against one
        // category, landing holds on the wrong buckets for one of them.
        List<Long> rules = decision.lines().stream()
                .map(PreAuthorizationDecision.Line::benefitRuleId).distinct().toList();
        assertThat(rules).as("two categories must resolve two rules").hasSize(2);

        // And the two lines therefore hold against different buckets.
        List<Long> buckets = decision.lines().stream()
                .flatMap(l -> l.limitHolds().stream())
                .map(PreAuthorizationDecision.LimitHold::bucketId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        assertThat(buckets).contains(sc.bucketId(), otherBucket);
    }
}
