package com.waad.tba.modules.claim.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * {@link ClaimRepository#existsUnresolvedLegacyClaimForEmployer} must read
 * the employer the LEGACY_UNRESOLVED claim's batch was actually filed under
 * (claim_batches.employer_id), not the member's CURRENT employer pointer --
 * exactly the "don't trust the current pointer" reason V217 exists. A member
 * transferred after the claim was created must not silently move the
 * precautionary lock off the employer it actually belongs to.
 *
 * No production path can create a LEGACY_UNRESOLVED row -- V219's own INSERT
 * guard (trg_claims_reject_new_legacy_unresolved) forbids it; that value
 * only ever comes from the V219 migration's one-time backfill of
 * pre-existing data. This test reproduces that backfill outcome directly via
 * SQL, disabling the guard the same way the migration itself does for its
 * own writes, to exercise the query against a row shaped like a real one.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimRepositoryUnresolvedLegacyLockIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimRepository claimRepository;
    @Autowired private JdbcTemplate jdbc;

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long unresolvedClaim(String tag, long memberId, long providerId, Long batchId) {
        long visitId = jdbc.queryForObject(
                "INSERT INTO visits (member_id, provider_id, visit_date) VALUES (?, ?, ?) RETURNING id",
                Long.class, memberId, providerId, LocalDate.now());
        jdbc.execute("ALTER TABLE claims DISABLE TRIGGER trg_claims_reject_new_legacy_unresolved");
        try {
            return jdbc.queryForObject(
                    "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, service_date, "
                            + "requested_amount, status, claim_context_code, claim_batch_id, "
                            + "historical_context_status) VALUES "
                            + "(?, ?, ?, ?, ?, ?, 'DRAFT', 'OUTPATIENT', ?, 'LEGACY_UNRESOLVED') RETURNING id",
                    Long.class, "CLM-LGK-" + tag, memberId, providerId, visitId, LocalDate.now(),
                    new BigDecimal("100.00"), batchId);
        } finally {
            jdbc.execute("ALTER TABLE claims ENABLE TRIGGER trg_claims_reject_new_legacy_unresolved");
        }
    }

    @Test
    void locksTheBatchsHistoricalEmployerNotTheMembersCurrentOne() {
        String s = suffix();
        long originalEmployerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "LGK-ORIG-" + s, "Original Employer " + s);
        long newEmployerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "LGK-NEW-" + s, "New Employer " + s);
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                        + "default_coverage_percent, start_date, end_date, status, active) VALUES "
                        + "(?, ?, ?, 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) "
                        + "RETURNING id",
                Long.class, "LGK-P-" + s, "LGKPOL-" + s, originalEmployerId);
        // Member's CURRENT pointer already moved to the new employer -- the
        // exact scenario the old, member.employer-based query would have
        // gotten wrong.
        long memberId = jdbc.queryForObject(
                "INSERT INTO members (employer_id, benefit_policy_id, full_name, card_number, barcode, "
                        + "status, active) VALUES (?, ?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, newEmployerId, policyId, "Legacy Lock Member", "LGKC-" + s, "LGKC-" + s);
        long providerId = jdbc.queryForObject(
                "INSERT INTO providers (name, license_number, provider_type) VALUES (?, ?, 'CLINIC') "
                        + "RETURNING id",
                Long.class, "LGK Provider " + s, "LGKLIC-" + s);
        long batchId = jdbc.queryForObject(
                "INSERT INTO claim_batches (batch_code, provider_id, employer_id, batch_year, batch_month, "
                        + "period_start, period_end, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN') RETURNING id",
                Long.class, "LGKB-" + s, providerId, originalEmployerId,
                LocalDate.now().getYear(), LocalDate.now().getMonthValue(),
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1));

        unresolvedClaim(s, memberId, providerId, batchId);

        assertThat(claimRepository.existsUnresolvedLegacyClaimForEmployer(originalEmployerId))
                .as("the batch's historical employer must be locked").isTrue();
        assertThat(claimRepository.existsUnresolvedLegacyClaimForEmployer(newEmployerId))
                .as("the member's current employer must NOT be falsely locked").isFalse();
    }

    @Test
    void fallsBackToTheMembersCurrentEmployerOnlyWhenNoBatchExists() {
        String s = suffix();
        long employerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "LGK2-" + s, "Fallback Employer " + s);
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                        + "default_coverage_percent, start_date, end_date, status, active) VALUES "
                        + "(?, ?, ?, 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) "
                        + "RETURNING id",
                Long.class, "LGK2-P-" + s, "LGK2POL-" + s, employerId);
        long memberId = jdbc.queryForObject(
                "INSERT INTO members (employer_id, benefit_policy_id, full_name, card_number, barcode, "
                        + "status, active) VALUES (?, ?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, employerId, policyId, "Legacy Lock Member 2", "LGK2C-" + s, "LGK2C-" + s);
        long providerId = jdbc.queryForObject(
                "INSERT INTO providers (name, license_number, provider_type) VALUES (?, ?, 'CLINIC') "
                        + "RETURNING id",
                Long.class, "LGK2 Provider " + s, "LGK2LIC-" + s);

        unresolvedClaim(s, memberId, providerId, null);

        assertThat(claimRepository.existsUnresolvedLegacyClaimForEmployer(employerId))
                .as("with no batch, the member's current employer is the only lead available")
                .isTrue();
    }
}
