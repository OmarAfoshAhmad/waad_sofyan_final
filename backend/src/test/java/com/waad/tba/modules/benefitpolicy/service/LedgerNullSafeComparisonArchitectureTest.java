package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Since V174 a ledger row may legitimately have no claim (a PREAUTH hold, an
 * OPENING_IMPORT opening balance) and no bucket (a POLICY_GENERAL movement).
 * That makes SQL's three-valued logic a financial hazard rather than a
 * curiosity:
 *
 *   claim_id <> 5   is NULL for a row with claim_id NULL, not TRUE,
 *                   so the row is dropped from the result
 *
 * Every one of these queries sums a member's consumption or holds. Dropping
 * rows understates what has been spent, which overstates what remains, which
 * lets the same limit be spent twice. The failure is silent: no error, just a
 * larger number than the truth.
 *
 * The convention, enforced here:
 *
 *   exclusion  ->  x is distinct from :p          (a null row is never "the excluded one")
 *   matching   ->  x is not distinct from :p      (null matches null, and nothing else)
 *
 * Postgres evaluates both without three-valued surprises, which is why they
 * are preferred over hand-written OR chains -- the OR form is easy to write
 * subtly wrong, and did get written wrong here once.
 */
class LedgerNullSafeComparisonArchitectureTest {

    private static final Path LEDGER_REPOSITORY = Path.of(
            "src/main/java/com/waad/tba/modules/benefitpolicy/repository/BenefitBucketConsumptionRepository.java");

    /**
     * Columns that became nullable in V174 (or are compared against a nullable
     * parameter), where a bare <> silently discards rows.
     */
    private static final List<String> NULLABLE_COMPARISONS = List.of(
            "claim_id <>",
            "claim.id <>",
            "claim_line_id <>",
            "bucket_id <>",
            "preauth_id <>",
            "preauth_line_id <>",
            "period_end <>");

    /**
     * A query that reads c.claim.serviceDate INNER JOINs through claim, so
     * every surviving row provably has one and <> cannot meet a null. Those
     * queries are claim-scoped by design (a service day is a fact of the
     * claim); the join is the guarantee, so the comparison is safe there and
     * only there.
     */
    private static final String INNER_JOINS_THROUGH_CLAIM = "c.claim.serviceDate";

    @Test
    void ledgerReadsNeverUseABareInequalityOnANullableColumn() throws IOException {
        assertThat(Files.exists(LEDGER_REPOSITORY)).as(LEDGER_REPOSITORY + " must exist").isTrue();
        String source = Files.readString(LEDGER_REPOSITORY, StandardCharsets.UTF_8);

        List<String> violations = new ArrayList<>();
        int lineNumber = 0;
        boolean claimJoinedQuery = false;
        for (String line : source.lines().toList()) {
            lineNumber++;
            String code = line.strip();

            if (code.startsWith("@Query")) {
                claimJoinedQuery = false; // a new query begins
            }
            if (code.contains(INNER_JOINS_THROUGH_CLAIM)) {
                claimJoinedQuery = true;
            }

            if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                continue; // the explanation above is allowed to quote the bug
            }
            for (String unsafe : NULLABLE_COMPARISONS) {
                if (code.contains(unsafe) && !claimJoinedQuery) {
                    violations.add(lineNumber + ": " + code);
                }
            }
        }

        assertThat(violations)
                .as("""
                        Use "is distinct from" to exclude and "is not distinct from" to match. \
                        A bare <> against a nullable column drops rows that have no claim or no \
                        bucket, which understates consumption and lets a limit be spent twice.""")
                .isEmpty();
    }

    /**
     * The general ceiling's period match is the specific place the OR form was
     * written wrong: a null upper bound matched EVERY period instead of the
     * open-ended one, so a hold from another year counted against this one.
     */
    @Test
    void theGeneralScopeReadMatchesItsPeriodNullSafely() throws IOException {
        String source = Files.readString(LEDGER_REPOSITORY, StandardCharsets.UTF_8);

        int queryStart = source.indexOf("sumGeneralScopeReserved");
        assertThat(queryStart).as("sumGeneralScopeReserved must exist").isNotEqualTo(-1);

        // The query text sits above the method signature.
        String queryBody = source.substring(0, queryStart);
        String lastQuery = queryBody.substring(queryBody.lastIndexOf("@Query"));

        assertThat(lastQuery)
                .as("the general-scope read must match period_end null-safely")
                .contains("period_end is not distinct from");
    }
}
