package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import com.waad.tba.modules.claim.dto.FinancialSummaryDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The batches screen draws one card per provider. Each card used to fetch its
 * own financial summary, so 146 active providers produced 146 simultaneous
 * requests; nginx rate-limits /api/ at 20r/s with a burst of 40 and refused the
 * rest with 503. The cards then rendered {@code summary?.totalClaimsAmount || 0}
 * -- so a refused request became "0.00", indistinguishable from a provider with
 * no claims at all. A money screen quietly reporting zero is worse than one
 * reporting an error.
 *
 * {@code getFinancialSummaryByProvider} answers for every provider in one
 * GROUP BY. The risk it introduces is drift: two aggregations meant to agree,
 * written separately, that slowly stop agreeing. This test exists to make that
 * impossible to do silently -- it asks BOTH paths about the same seeded claims
 * and requires every field of every provider's summary to match exactly.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class FinancialSummaryPerProviderParityIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long employerId;
    private long providerA;
    private long providerB;
    private long providerC;
    private final LocalDate serviceDate = LocalDate.now().minusDays(3);

    @BeforeEach
    void seedThreeProvidersWithDifferentShapes() {
        String s = suffix();
        String username = "parity-" + s;
        users.save(User.builder().username(username).password("x").fullName("Parity Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));

        employerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "PAR-" + s, "جهة تكافؤ الملخص " + s);
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status, active)"
                        + " VALUES (?, ?, ?, ?, ?, 100000, 100, 'ACTIVE', true) RETURNING id",
                Long.class, "وثيقة تكافؤ " + s, "PAR-POL-" + s, employerId,
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        long memberId = jdbc.queryForObject(
                "INSERT INTO members (employer_id, benefit_policy_id, full_name, card_number, barcode,"
                        + " status, active) VALUES (?, ?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                // chk_members_barcode_equals_card_number: the two must be identical.
                Long.class, employerId, policyId, "عضو تكافؤ", "PAR-C-" + s, "PAR-C-" + s);

        // Three deliberately different shapes, so a mapper that mixes up two
        // columns cannot pass by coincidence.
        providerA = provider(s, "A");
        claim(memberId, providerA, "APPROVED", 500, 400, 100, 380, 20);
        claim(memberId, providerA, "SETTLED", 300, 300, 0, 300, 0);

        providerB = provider(s, "B");
        claim(memberId, providerB, "DRAFT", 900, 0, 0, 0, 0);

        // A provider with claims outside the window: it must appear in neither
        // path for this period, and both must agree on that too.
        providerC = provider(s, "C");
        claimOn(memberId, providerC, LocalDate.now().minusYears(1), "APPROVED", 700, 700, 0, 700, 0);
    }

    private long provider(String s, String tag) {
        return jdbc.queryForObject(
                "INSERT INTO providers (name, license_number, provider_type, active)"
                        + " VALUES (?, ?, 'CLINIC', true) RETURNING id",
                Long.class, "مقدم " + tag + " " + s, "PAR-LIC-" + tag + "-" + s);
    }

    private void claim(long memberId, long providerId, String status,
            int requested, int approved, int refused, int netProvider, int discount) {
        claimOn(memberId, providerId, serviceDate, status, requested, approved, refused, netProvider, discount);
    }

    private void claimOn(long memberId, long providerId, LocalDate date, String status,
            int requested, int approved, int refused, int netProvider, int discount) {
        long visitId = jdbc.queryForObject(
                "INSERT INTO visits (member_id, provider_id, visit_date) VALUES (?, ?, ?) RETURNING id",
                Long.class, memberId, providerId, date);
        jdbc.update("INSERT INTO claims (claim_number, member_id, provider_id, visit_id, service_date,"
                        + " requested_amount, approved_amount, refused_amount, net_provider_amount,"
                        + " company_discount_amount, status, claim_context_code, active)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OUTPATIENT', true)",
                "CLM-PAR-" + UUID.randomUUID().toString().substring(0, 12),
                memberId, providerId, visitId, date,
                requested, approved, refused, netProvider, discount, status);
    }

    @Test
    @DisplayName("the grouped summary equals the per-provider summary, field for field")
    void groupedAndSingleProviderPathsAgreeExactly() {
        LocalDate from = serviceDate.minusDays(1);
        LocalDate to = serviceDate.plusDays(1);

        Map<Long, FinancialSummaryDto> grouped =
                claimService.getFinancialSummaryByProvider(employerId, null, from, to);

        for (long providerId : new long[] { providerA, providerB }) {
            FinancialSummaryDto single =
                    claimService.getFinancialSummary(employerId, providerId, null, from, to);
            FinancialSummaryDto fromGroup = grouped.get(providerId);

            assertThat(fromGroup)
                    .as("provider %d has claims in this window, so the grouped read must carry it", providerId)
                    .isNotNull();

            assertThat(fromGroup.getClaimsCount()).as("claimsCount for %d", providerId)
                    .isEqualTo(single.getClaimsCount());
            assertThat(fromGroup.getApprovedCount()).as("approvedCount for %d", providerId)
                    .isEqualTo(single.getApprovedCount());
            assertThat(fromGroup.getSettledCount()).as("settledCount for %d", providerId)
                    .isEqualTo(single.getSettledCount());
            assertThat(fromGroup.getTotalClaimsAmount()).as("totalClaimsAmount for %d", providerId)
                    .isEqualByComparingTo(single.getTotalClaimsAmount());
            assertThat(fromGroup.getTotalApprovedAmount()).as("totalApprovedAmount for %d", providerId)
                    .isEqualByComparingTo(single.getTotalApprovedAmount());
            assertThat(fromGroup.getTotalRefusedAmount()).as("totalRefusedAmount for %d", providerId)
                    .isEqualByComparingTo(single.getTotalRefusedAmount());
            assertThat(fromGroup.getTotalPaidAmount()).as("totalPaidAmount for %d", providerId)
                    .isEqualByComparingTo(single.getTotalPaidAmount());
            assertThat(fromGroup.getOutstandingAmount()).as("outstandingAmount for %d", providerId)
                    .isEqualByComparingTo(single.getOutstandingAmount());
            assertThat(fromGroup.getTotalCompanyDiscountAmount())
                    .as("totalCompanyDiscountAmount for %d", providerId)
                    .isEqualByComparingTo(single.getTotalCompanyDiscountAmount());
        }
    }

    @Test
    @DisplayName("a provider whose only claims fall outside the window is absent from both paths")
    void aProviderOutsideTheWindowIsAbsentFromBoth() {
        LocalDate from = serviceDate.minusDays(1);
        LocalDate to = serviceDate.plusDays(1);

        Map<Long, FinancialSummaryDto> grouped =
                claimService.getFinancialSummaryByProvider(employerId, null, from, to);
        FinancialSummaryDto single =
                claimService.getFinancialSummary(employerId, providerC, null, from, to);

        assertThat(grouped).as("absent from the grouped map entirely").doesNotContainKey(providerC);
        assertThat(single.getClaimsCount())
                .as("and reported as zero by the single-provider path -- the two must not disagree "
                        + "about a provider that simply has nothing in this period")
                .isZero();
    }

    @Test
    @DisplayName("one call carries every provider that has claims in the window")
    void oneCallCarriesEveryProviderInTheWindow() {
        Map<Long, FinancialSummaryDto> grouped = claimService.getFinancialSummaryByProvider(
                employerId, null, serviceDate.minusDays(1), serviceDate.plusDays(1));

        assertThat(grouped.keySet())
                .as("the whole point of the endpoint: the screen asks once and receives all of them, "
                        + "instead of one request per card")
                .contains(providerA, providerB);
    }
}
