package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * How occurrences are counted when the structure is not a single flat bucket:
 * an inherited parent, and several lines sharing one ceiling.
 *
 * Both cases decide how much of a member's limit is held, and both are easy
 * to get wrong in the same direction -- holding too little, so a second
 * approval takes a visit that is already spoken for.
 */
@SpringBootTest(classes = TbaWaadApplication.class,
        properties = "waad.preauth.validity-days=30")
@ActiveProfiles("test")
class PreAuthCountingShapeIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthReservationLedgerService service;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record World(long preauthId, long memberId, long policyId,
            long childBucketId, long parentBucketId) {}

    /**
     * A rule linked to a MONETARY child whose PARENT carries the occurrence
     * ceiling -- the shape where a count-only ancestor is easiest to miss.
     *
     * @param countingMethod applied to the parent
     * @param lineCount      how many lines the approval carries
     * @param quantityEach   approved quantity per line
     */
    private World world(String countingMethod, int parentTimesLimit, int lineCount, int quantityEach) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('PC-" + s
                + "', 'Parent Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES ('PP-" + s
                + "', 'PPOL-" + s + "', " + employerId
                + ", 1000000, 80, CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id",
                Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Parent Member', "
                + policyId + ", 'PC" + s + "', 'PC" + s + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);

        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('PCAT-" + s + "', 'Parent Category', true) RETURNING id", Long.class);
        Long serviceId = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, active) "
                + "VALUES ('PSRV-" + s + "', 'Parent Service', " + categoryId + ", true) RETURNING id",
                Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, claim_context_code, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', 'OUTPATIENT', 80, true, false) RETURNING id", Long.class);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'PG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);

        // The parent carries the occurrence ceiling and no money.
        Long parentBucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, "
                + "benefit_group_id, code, name_ar, times_limit, period_type, counting_method, "
                + "consumption_basis, benefit_scope_type, context_type, active) VALUES (" + policyId + ", "
                + groupId + ", 'PB-PARENT-" + s + "', 'وعاء أب', " + parentTimesLimit + ", 'ANNUAL', '"
                + countingMethod + "', 'COMPANY_SHARE', 'GROUP', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        // The child carries money and no count, and is the one the rule links.
        Long childBucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, "
                + "benefit_group_id, parent_bucket_id, code, name_ar, amount_limit, period_type, "
                + "counting_method, consumption_basis, benefit_scope_type, context_type, active) VALUES ("
                + policyId + ", " + groupId + ", " + parentBucketId + ", 'PB-CHILD-" + s
                + "', 'وعاء ابن', 1000000, 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', 'CATEGORY', "
                + "'OUTPATIENT', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)",
                ruleId, childBucketId);

        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) "
                + "VALUES ('Prov " + s + "', 'PLIC-" + s + "', 'CLINIC') RETURNING id", Long.class);
        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "provider_id, service_category_id, status, request_date, expected_service_date, "
                + "created_at, updated_at, version) VALUES (" + memberId + ", " + policyId + ", "
                + providerId + ", " + categoryId + ", 'SUBMITTED', now(), CURRENT_DATE + 14, now(), "
                + "now(), 0) RETURNING id", Long.class);

        for (int i = 0; i < lineCount; i++) {
            jdbc.update("INSERT INTO pre_authorization_lines (pre_authorization_id, provider_service_id, "
                    + "medical_service_id, medical_category_id, provider_service_code, service_name, "
                    + "contract_price, requested_amount, coverage_percentage, encounter_type, "
                    + "requested_quantity, approved_quantity) VALUES (?, " + serviceId + ", " + serviceId
                    + ", " + categoryId + ", ?, ?, 500.00, 500.00, 80, 'OUTPATIENT', "
                    + quantityEach + ", " + quantityEach + ")",
                    preauthId, "SVC-" + s + "-" + i, "Service " + i);
        }
        return new World(preauthId, memberId, policyId, childBucketId, parentBucketId);
    }

    private int heldTimesOn(long preauthId, long bucketId) {
        Integer held = jdbc.queryForObject(
                "SELECT COALESCE(SUM(times_consumed), 0) FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                Integer.class, preauthId, bucketId);
        return held == null ? 0 : held;
    }

    private long holdRowsOn(long preauthId, long bucketId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                Long.class, preauthId, bucketId);
    }

    // ── the inherited ceiling ───────────────────────────────────────────

    @Test
    void aCountingParentIsFoundThroughAMonetaryChildAndHeldOnce() {
        World w = world("PER_VISIT", 3, 1, 1);

        service.approveAndReserve(w.preauthId(), 0L, "reviewer");

        // The rule links only the child, which caps money and counts nothing.
        // Missing the parent would let the visit ceiling be exceeded silently.
        assertThat(heldTimesOn(w.preauthId(), w.parentBucketId()))
                .as("the inherited occurrence ceiling must be held").isEqualTo(1);

        // Reached once, recorded once -- even though the walk can arrive at a
        // parent from more than one path.
        assertThat(holdRowsOn(w.preauthId(), w.parentBucketId()))
                .as("no duplicate row for a parent reached twice").isEqualTo(1L);
    }

    // ── several lines, one ceiling ──────────────────────────────────────

    @Test
    void perVisitHoldsOneOccurrenceForTheWholeApprovalHoweverManyLines() {
        World w = world("PER_VISIT", 5, 3, 1);

        service.approveAndReserve(w.preauthId(), 0L, "reviewer");

        // A visit is one visit. Three lines of the same encounter must not
        // consume three of them.
        assertThat(heldTimesOn(w.preauthId(), w.parentBucketId())).isEqualTo(1);
    }

    @Test
    void eachLineHoldsOneOccurrencePerLine() {
        World w = world("EACH_LINE", 5, 3, 1);

        service.approveAndReserve(w.preauthId(), 0L, "reviewer");

        assertThat(heldTimesOn(w.preauthId(), w.parentBucketId())).isEqualTo(3);
    }

    @Test
    void eachUnitSumsTheApprovedQuantitiesAndNotTheRequestedOnes() {
        World w = world("EACH_UNIT", 20, 2, 4);
        // The reviewer cut each line from 4 units to 3.
        jdbc.update("UPDATE pre_authorization_lines SET approved_quantity = 3, "
                + "review_decision = 'PARTIALLY_APPROVE', rejection_reason = 'الكمية تتجاوز المبرر' "
                + "WHERE pre_authorization_id = ?", w.preauthId());

        service.approveAndReserve(w.preauthId(), 0L, "reviewer");

        // 3 + 3, not 4 + 4: a refused unit is not an occurrence.
        assertThat(heldTimesOn(w.preauthId(), w.parentBucketId())).isEqualTo(6);
    }

    @Test
    void cancellingReturnsTheWholeAggregatedOccurrenceCountExactlyOnce() {
        World w = world("EACH_LINE", 5, 3, 1);
        service.approveAndReserve(w.preauthId(), 0L, "reviewer");
        assertThat(heldTimesOn(w.preauthId(), w.parentBucketId())).isEqualTo(3);

        service.cancelAndRelease(w.preauthId(), "طلب المستفيد", "reviewer");

        Integer released = jdbc.queryForObject(
                "SELECT COALESCE(SUM(times_consumed), 0) FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'REVERSED'",
                Integer.class, w.preauthId(), w.parentBucketId());

        assertThat(released).as("everything held comes back, once").isEqualTo(3);
    }
}
