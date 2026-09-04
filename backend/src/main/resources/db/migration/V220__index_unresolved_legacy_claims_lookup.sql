-- BenefitPolicyService#policyEditBlockReason now runs
-- ClaimRepository#existsUnresolvedLegacyClaimForEmployer on every policy
-- edit/activate/draft-revert/structure-change check (V219). LEGACY_UNRESOLVED
-- rows are rare by construction -- only V219's own backfill can create one --
-- so a partial index scoped to that value costs almost nothing to maintain
-- and keeps this lookup from becoming a claims-table scan as the table
-- grows. Two indexes, matching the query's two branches: the primary lookup
-- by claim_batch_id, and the member_id fallback for the rare claim with no
-- batch at all.
CREATE INDEX IF NOT EXISTS idx_claims_legacy_unresolved_batch
    ON claims (claim_batch_id)
    WHERE historical_context_status = 'LEGACY_UNRESOLVED' AND claim_batch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_claims_legacy_unresolved_member
    ON claims (member_id)
    WHERE historical_context_status = 'LEGACY_UNRESOLVED' AND claim_batch_id IS NULL;
