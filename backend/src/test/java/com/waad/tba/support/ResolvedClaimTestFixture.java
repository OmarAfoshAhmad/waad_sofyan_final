package com.waad.tba.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * C1: a claim row may only claim {@code historicalContextStatus = RESOLVED}
 * when its {@code policyId}/{@code policyAssignmentId}/{@code employerAssignmentId}
 * are all present (V219's {@code chk_claims_historical_context_consistency}).
 * An adjacent-module test that just needs SOME valid claim row to anchor its
 * own scenario -- a member-merge target, a provider-contract foreign key, a
 * settlement payment allocation -- still has to satisfy that constraint
 * honestly, not with three arbitrary ids.
 *
 * This does not create assignments itself: it reads back the ids
 * {@link PostgresIntegrationTestBase#initializeTemporalAssignments} already
 * created through the real temporal-assignment resolvers, so a test never
 * carries a second, fixture-only reimplementation of that resolution.
 */
public final class ResolvedClaimTestFixture {

    private ResolvedClaimTestFixture() {
    }

    /** Everything a {@code Claim} row needs to be honestly RESOLVED under V219. */
    public record ResolvedContext(Long policyId, Long policyAssignmentId, Long employerAssignmentId) {
    }

    /**
     * @param memberId a member whose employer+policy assignments were already
     *                 created via {@link PostgresIntegrationTestBase#initializeTemporalAssignments}
     *                 -- the assignment covering the claim's own serviceDate.
     */
    public static ResolvedContext resolve(JdbcTemplate jdbc, Long memberId) {
        Long policyAssignmentId = jdbc.queryForObject(
                "SELECT id FROM member_policy_assignments WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, memberId);
        Long employerAssignmentId = jdbc.queryForObject(
                "SELECT id FROM member_employer_assignments WHERE member_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, memberId);
        Long policyId = jdbc.queryForObject(
                "SELECT policy_id FROM member_policy_assignments WHERE id = ?",
                Long.class, policyAssignmentId);
        return new ResolvedContext(policyId, policyAssignmentId, employerAssignmentId);
    }
}
