package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.dto.MemberLimitDetail;
import com.waad.tba.modules.member.security.MemberAccessDeniedException;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The drawer, and the one property that makes it worth opening: it must not
 * tell a different story from the row it was opened from.
 *
 * The general figures come from the same read the column uses, so agreement is
 * structural rather than coincidental. The buckets sit beside them and are
 * never added to them -- one claim line can map to several buckets, so summing
 * would count the same money once per category it fell into.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberLimitDetailIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberLimitDetailService detailService;
    @Autowired private MemberLimitOverviewService overviewService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    private static final String YEAR_START = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String YEAR_END =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private long employerId;
    private long policyId;
    private long memberId;
    private long assignmentId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @BeforeEach
    void seed() {
        String s = suffix();
        employerId = jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Drawer Employer " + s + "', 'DR-" + s + "', true) RETURNING id", Long.class);
        policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "start_date, end_date, annual_limit, default_coverage_percent, status, active) VALUES "
                + "('Drawer Policy', ?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 80, 'ACTIVE', true) RETURNING id",
                Long.class, "DR-" + s, employerId, new BigDecimal("60000.00"));
        memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Drawer Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "DR-" + s, employerId, policyId);
        assignmentId = jdbc.queryForObject(
                "INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                        + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL') RETURNING id",
                Long.class, memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", memberId, employerId);
        signIn();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void signIn() {
        String username = "drawer-" + suffix();
        userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Drawer Admin")
                .email(username + "@waad.ly").userType("EMPLOYER_ADMIN")
                .employerId(employerId).active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    private long bucket(String code, String amountLimit) {
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (?, ?, 'مجموعة اختبار', 'ANY', "
                + "'INDIVIDUAL') RETURNING id", Long.class, policyId, "GRP-" + code);
        return jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, "
                + "code, name_ar, context_type, amount_limit, period_type, counting_method, "
                + "consumption_basis, limit_role, benefit_scope_type, beneficiary_scope_type, active) "
                + "VALUES (?, ?, ?, 'وعاء اختبار', 'ANY', ?, 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', "
                + "'STANDARD', 'CATEGORY', 'MEMBER', true) RETURNING id",
                Long.class, policyId, groupId, code,
                amountLimit == null ? null : new BigDecimal(amountLimit));
    }

    private void spendOnBucket(long bucketId, String amount) {
        Long batchId = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'opening balance for test', 'tester', 'test') "
                        + "RETURNING id", Long.class, "DR-BATCH-" + suffix());
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, "
                + "period_start, period_end, approved_amount, times_consumed, calculation_version, "
                + "idempotency_key, status, source_type, limit_scope, opening_batch_id, created_at) "
                + "VALUES (?, ?, ?, " + YEAR_START + ", " + YEAR_END + ", ?, 0, 1, ?, 'COMMITTED', "
                + "'OPENING_IMPORT', 'BUCKET', ?, now())",
                policyId, memberId, bucketId, new BigDecimal(amount), "DR-C-" + suffix(), batchId);
    }

    private void holdOnBucket(long bucketId, String amount) {
        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (?, ?, 'APPROVED', now(), "
                + "now(), now()) RETURNING id", Long.class, memberId, policyId);
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (?, ?) RETURNING id", Long.class, preauthId,
                new BigDecimal(amount));
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, "
                + "preauth_id, preauth_line_id, member_policy_assignment_id, period_start, period_end, "
                + "approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + "source_type, limit_scope, created_at) VALUES (?, ?, ?, ?, ?, ?, " + YEAR_START + ", "
                + YEAR_END + ", ?, 0, 1, ?, 'RESERVED', 'PREAUTH', 'BUCKET', now())",
                policyId, memberId, bucketId, preauthId, lineId, assignmentId,
                new BigDecimal(amount), "DR-R-" + suffix());
    }

    private void spendGeneral(String amount) {
        Long batchId = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'opening balance for test', 'tester', 'test') "
                        + "RETURNING id", Long.class, "DR-GBATCH-" + suffix());
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, "
                + "period_end, approved_amount, times_consumed, calculation_version, idempotency_key, "
                + "status, source_type, limit_scope, opening_batch_id, created_at) VALUES (?, ?, "
                + YEAR_START + ", " + YEAR_END + ", ?, 0, 1, ?, 'COMMITTED', 'OPENING_IMPORT', "
                + "'POLICY_GENERAL', ?, now())",
                policyId, memberId, new BigDecimal(amount), "DR-GC-" + suffix(), batchId);
    }

    @Test
    void theDrawerAndTheColumnTellTheSameStoryForTheSameMember() {
        spendGeneral("10000.00");

        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);
        CurrentGeneralLimitSummary row = overviewService.summariesFor(List.of(memberId)).get(memberId);

        // Stated numbers as well as equality: two surfaces agreeing on the
        // wrong figure is the failure this is guarding, and a bare comparison
        // would pass through it.
        assertThat(detail.general().reservableAvailable()).isEqualByComparingTo("50000.00");
        assertThat(row.reservableAvailable()).isEqualByComparingTo("50000.00");

        assertThat(detail.asOfDate()).isEqualTo(row.asOfDate());
        assertThat(detail.general().mode()).isEqualTo(row.mode());
        assertThat(detail.general().limit()).isEqualByComparingTo(row.limit());
        assertThat(detail.general().committed()).isEqualByComparingTo(row.committed());
        assertThat(detail.general().reserved()).isEqualByComparingTo(row.reserved());
        assertThat(detail.general().actualRemaining()).isEqualByComparingTo(row.actualRemaining());
    }

    @Test
    void everySummaryCarriesTheDateAndInstantItWasReadFor() {
        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);

        assertThat(detail.asOfDate()).isNotNull();
        assertThat(detail.readAt())
                .as("the column and the drawer are separate reads; a claim landing between "
                        + "them makes the figures differ honestly, and the instant is how "
                        + "a reader can tell that from one screen being wrong")
                .isNotNull();
    }

    @Test
    void aBucketCarriesTheSameFiveFiguresTheGeneralCeilingDoes() {
        long dental = bucket("DENTAL-" + suffix(), "5000.00");
        spendOnBucket(dental, "1200.00");
        holdOnBucket(dental, "800.00");

        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);
        MemberLimitDetail.BucketBalance balance = detail.buckets().stream()
                .filter(b -> b.bucketId() == dental).findFirst().orElseThrow();

        assertThat(balance.limit()).isEqualByComparingTo("5000.00");
        assertThat(balance.committed()).isEqualByComparingTo("1200.00");
        assertThat(balance.reserved()).isEqualByComparingTo("800.00");
        assertThat(balance.actualRemaining()).isEqualByComparingTo("3800.00");
        assertThat(balance.reservableAvailable())
                .as("the same distinction the general ceiling makes, so a reader does not "
                        + "have to learn two layouts")
                .isEqualByComparingTo("3000.00");
    }

    @Test
    void bucketsAreNeverAddedToTheGeneralCeiling() {
        spendGeneral("10000.00");
        long dental = bucket("DENTAL-" + suffix(), "5000.00");
        spendOnBucket(dental, "1200.00");

        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);

        assertThat(detail.general().committed())
                .as("one claim line can map to several buckets, so adding bucket "
                        + "consumption to the general figure counts the same money once "
                        + "per category it fell into")
                .isEqualByComparingTo("10000.00");
        assertThat(detail.general().reservableAvailable()).isEqualByComparingTo("50000.00");
    }

    @Test
    void aBucketWithNoActivityIsListedAtZeroRatherThanOmitted() {
        long unused = bucket("OPTICAL-" + suffix(), "2000.00");

        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);
        MemberLimitDetail.BucketBalance balance = detail.buckets().stream()
                .filter(b -> b.bucketId() == unused).findFirst().orElseThrow();

        assertThat(balance.committed())
                .as("omitting it would make 'nothing spent here' look the same as "
                        + "'this benefit does not exist for you'")
                .isEqualByComparingTo("0.00");
        assertThat(balance.reservableAvailable()).isEqualByComparingTo("2000.00");
    }

    @Test
    void aCountOnlyBucketReportsNoMonetaryBalanceRatherThanZero() {
        long visits = bucket("VISITS-" + suffix(), null);

        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);
        MemberLimitDetail.BucketBalance balance = detail.buckets().stream()
                .filter(b -> b.bucketId() == visits).findFirst().orElseThrow();

        assertThat(balance.limit()).isNull();
        assertThat(balance.actualRemaining())
                .as("null means this ceiling does not measure money, never 'no money left'")
                .isNull();
        assertThat(balance.reservableAvailable()).isNull();
    }

    @Test
    void anOverspentBucketStaysNegative() {
        long dental = bucket("DENTAL-" + suffix(), "5000.00");
        spendOnBucket(dental, "6000.00");

        MemberLimitDetail detail = detailService.authorizedDetailFor(memberId);
        MemberLimitDetail.BucketBalance balance = detail.buckets().stream()
                .filter(b -> b.bucketId() == dental).findFirst().orElseThrow();

        assertThat(balance.actualRemaining()).isEqualByComparingTo("-1000.00");
    }

    @Test
    void aMemberOutsideTheCallersScopeIsRefused() {
        String s = suffix();
        Long otherEmployerId = jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Other Drawer " + s + "', 'ODR-" + s + "', true) RETURNING id", Long.class);
        Long otherMemberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, "
                + "employer_id, status, active) VALUES ('Outside Member', ?, ?, 'PENDING', false) "
                + "RETURNING id", Long.class, "ODR-" + s, otherEmployerId);

        assertThatThrownBy(() -> detailService.authorizedDetailFor(otherMemberId))
                .isInstanceOf(MemberAccessDeniedException.class);
    }
}
