-- ============================================================
-- finance-02.4: replace the name-based "policy annual mirror" heuristic
-- (BenefitBucketLimitService.isLegacyPolicyAnnualMirror -- bucket.code =
-- 'B-GENERAL' or group.code = 'G-GENERAL') with an explicit, DB-enforced
-- semantic role on the bucket itself. ApplicableLimitResolver (WAAD-FIN-1.0)
-- must be able to exclude the bucket that mirrors policy.annualLimit without
-- guessing from a name, which breaks silently on rename/import.
--
-- No production claims exist yet on this branch (constitution S26), so this
-- is the only safe window to backfill without a legacy-data audit.
-- ============================================================

ALTER TABLE benefit_limit_buckets
    ADD COLUMN limit_role VARCHAR(30) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE benefit_limit_buckets
    ADD CONSTRAINT chk_bucket_limit_role CHECK (limit_role IN ('STANDARD', 'POLICY_GENERAL_MIRROR'));

-- At most one active POLICY_GENERAL_MIRROR bucket per policy -- two would mean
-- two competing definitions of the same general ceiling.
CREATE UNIQUE INDEX uq_bucket_policy_general_mirror
    ON benefit_limit_buckets (policy_id)
    WHERE limit_role = 'POLICY_GENERAL_MIRROR' AND active = true;

-- ── One-time backfill, name-heuristic used exactly once, here, never again ──
--
-- A candidate is a bucket whose amount/period exactly matches its policy's
-- annualLimit and whose code (or group code) matches the legacy convention.
-- If any policy has more than one such candidate, the ambiguity is real and
-- must be resolved by hand -- the migration aborts rather than guessing.
DO $$
DECLARE
    ambiguous_policy_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO ambiguous_policy_count
    FROM (
        SELECT b.policy_id
        FROM benefit_limit_buckets b
        JOIN benefit_groups g ON g.id = b.benefit_group_id
        JOIN benefit_policies p ON p.id = b.policy_id
        WHERE b.active = true
          AND b.period_type = 'ANNUAL'
          AND b.amount_limit = p.annual_limit
          AND (b.code = 'B-GENERAL' OR g.code = 'G-GENERAL')
        GROUP BY b.policy_id
        HAVING COUNT(*) > 1
    ) ambiguous;

    IF ambiguous_policy_count > 0 THEN
        RAISE EXCEPTION 'POLICY_GENERAL_MIRROR_MISMATCH: % polic(y/ies) have more than one candidate mirror bucket -- tag limit_role manually before this migration can proceed', ambiguous_policy_count;
    END IF;
END $$;

UPDATE benefit_limit_buckets b
SET limit_role = 'POLICY_GENERAL_MIRROR'
FROM benefit_groups g, benefit_policies p
WHERE g.id = b.benefit_group_id
  AND p.id = b.policy_id
  AND b.active = true
  AND b.period_type = 'ANNUAL'
  AND b.amount_limit = p.annual_limit
  AND (b.code = 'B-GENERAL' OR g.code = 'G-GENERAL');

COMMENT ON COLUMN benefit_limit_buckets.limit_role IS
    'STANDARD (default): a real, independent limit. POLICY_GENERAL_MIRROR: this '
    'bucket duplicates policy.annual_limit and must never be treated as an '
    'independent limit -- no semantic key, no snapshot row, no consumption of '
    'its own. ApplicableLimitResolver represents the general ceiling exactly '
    'once, as the synthetic POLICY_GENERAL:{policyId} entry, and excludes any '
    'bucket tagged POLICY_GENERAL_MIRROR from the applicable-limits list. '
    'Any drift between a mirror bucket''s amount_limit and its policy''s '
    'annual_limit is a POLICY_GENERAL_MIRROR_MISMATCH -- fail closed, never '
    'silently pick one value.';
