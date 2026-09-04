package com.waad.tba.modules.claim.entity;

/**
 * Whether {@code Claim.policyId}/{@code policyAssignmentId}/
 * {@code employerAssignmentId} (V217) are trustworthy enough to build a
 * policy-edit lock, a report, or a recalculation on.
 *
 * {@link #LEGACY_UNRESOLVED} is reserved for pre-V217 claims the V219
 * migration's backfill could not attribute without guessing (see
 * docs/testing/CLAIMS_POLICY_SNAPSHOT_BACKFILL_GAPS.md). No new claim may
 * ever be created with this status -- {@code MemberContextResolver
 * #resolveForOrFail} fails closed before a claim is ever built, so a claim
 * that exists always has a real, resolved context; the database itself
 * refuses an INSERT that claims otherwise (see V219's
 * {@code trg_claims_reject_new_legacy_unresolved}). The only legal
 * transition is {@code LEGACY_UNRESOLVED -> RESOLVED}, via a reviewed
 * correction that the database validates for consistency at the moment it
 * happens; {@link #RESOLVED} is permanent once entered.
 */
public enum ClaimHistoricalContextStatus {
    RESOLVED,
    LEGACY_UNRESOLVED
}
