-- A Claim resolves the member's employer/policy/policy-assignment by
-- serviceDate at creation time (MemberContextResolver, via
-- DirectClaimEntryService / ClaimService.createClaim), but never wrote that
-- resolved identity onto the claim itself. Any later path that needs "which
-- policy/employer applied to this claim" (recalculation, a locked-policy
-- check, a report) re-asks MemberContextResolver with the same serviceDate --
-- and if the member's assignment history changed in between (a transfer, a
-- mid-term policy change), the second answer can legitimately differ from
-- the first. That is a second source of truth for a fact that must never
-- drift once a claim exists.
--
-- This records the resolved identity once, at creation, as a permanent
-- historical fact of the claim -- the same pattern V172/V187 already used
-- for claim_line_limit_snapshots and benefit_bucket_consumptions.
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS policy_id             BIGINT,
    ADD COLUMN IF NOT EXISTS policy_assignment_id  BIGINT,
    ADD COLUMN IF NOT EXISTS employer_assignment_id BIGINT;

COMMENT ON COLUMN claims.policy_id IS
    'Benefit policy resolved for this claim''s member at serviceDate, captured at claim creation. Immutable once the claim leaves DRAFT/NEEDS_CORRECTION.';
COMMENT ON COLUMN claims.policy_assignment_id IS
    'member_policy_assignments row that was in force at serviceDate when this claim was created. Immutable once the claim leaves DRAFT/NEEDS_CORRECTION.';
COMMENT ON COLUMN claims.employer_assignment_id IS
    'member_employer_assignments row that was in force at serviceDate when this claim was created. Immutable once the claim leaves DRAFT/NEEDS_CORRECTION.';

-- FKs added NOT VALID: existing rows are backfilled below on a best-effort
-- basis and may still legitimately be NULL for old/ambiguous claims, so
-- validating immediately would fail deployment. VALIDATE CONSTRAINT runs in
-- a later migration once the backfill gap report (below) has been reviewed.
ALTER TABLE claims
    ADD CONSTRAINT fk_claims_policy
        FOREIGN KEY (policy_id) REFERENCES benefit_policies(id) ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_claims_policy_assignment
        FOREIGN KEY (policy_assignment_id) REFERENCES member_policy_assignments(id) ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_claims_employer_assignment
        FOREIGN KEY (employer_assignment_id) REFERENCES member_employer_assignments(id) ON DELETE RESTRICT NOT VALID;

-- idx_claims_policy backs the future countByPolicyId-style read (policy lock
-- checks, reports) that this snapshot exists to make possible; it belongs
-- with this migration rather than waiting for the caller change, since that
-- change (V218 territory) has no reason to also be an index migration.
CREATE INDEX IF NOT EXISTS idx_claims_policy ON claims(policy_id) WHERE policy_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_claims_policy_assignment ON claims(policy_assignment_id) WHERE policy_assignment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_claims_employer_assignment ON claims(employer_assignment_id) WHERE employer_assignment_id IS NOT NULL;

-- ============================================================
-- Backfill, best-effort, no guessing on ambiguous rows.
-- ============================================================

-- Gap log: every claim this migration could not confidently backfill, with
-- why. Reviewed manually before VALIDATE CONSTRAINT / NOT NULL land.
--
-- Composite PK, not claim_id alone: a claim can be missing BOTH policy_id
-- and employer_assignment_id at once, and each is logged as its own row.
-- A claim_id-only PK would let the second INSERT's ON CONFLICT DO NOTHING
-- silently drop that second gap -- exactly the kind of guess-by-omission
-- this table exists to prevent.
CREATE TABLE IF NOT EXISTS claims_historical_context_backfill_gaps (
    claim_id     BIGINT NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    missing      VARCHAR(30) NOT NULL,
    reason       VARCHAR(500) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (claim_id, missing)
);

-- Source 1 for policy_id/policy_assignment_id: claim_line_limit_snapshots,
-- the append-only record of what was actually adjudicated. Only trusted when
-- every snapshot row for the claim agrees on one value.
WITH snapshot_agreement AS (
    SELECT claim_id,
           MIN(policy_id) AS policy_id,
           COUNT(DISTINCT policy_id) AS distinct_policy,
           MIN(member_policy_assignment_id) AS policy_assignment_id,
           COUNT(DISTINCT member_policy_assignment_id) FILTER (WHERE member_policy_assignment_id IS NOT NULL)
               AS distinct_assignment
    FROM claim_line_limit_snapshots
    GROUP BY claim_id
)
UPDATE claims c
   SET policy_id = s.policy_id,
       policy_assignment_id = CASE WHEN s.distinct_assignment = 1 THEN s.policy_assignment_id ELSE NULL END
  FROM snapshot_agreement s
 WHERE c.id = s.claim_id
   AND s.distinct_policy = 1
   AND c.policy_id IS NULL;

-- Source 2 for policy_id, when no adjudication snapshot exists: resolve by
-- serviceDate against the member's assignment history directly, same
-- question MemberContextResolver asks, answered once here instead of live.
WITH resolved AS (
    SELECT c.id AS claim_id, a.policy_id, a.id AS policy_assignment_id,
           COUNT(*) OVER (PARTITION BY c.id) AS match_count
    FROM claims c
    JOIN member_policy_assignments a ON a.member_id = c.member_id
    WHERE c.policy_id IS NULL
      AND c.service_date IS NOT NULL
      AND a.assignment_start_date <= c.service_date
      AND (a.assignment_end_date IS NULL OR a.assignment_end_date > c.service_date)
)
UPDATE claims c
   SET policy_id = r.policy_id,
       policy_assignment_id = r.policy_assignment_id
  FROM resolved r
 WHERE c.id = r.claim_id
   AND r.match_count = 1;

-- employer_assignment_id: same by-serviceDate resolution against employer
-- assignment history. No claim_line_limit_snapshots equivalent exists for
-- employer, so this is the only source.
WITH resolved AS (
    SELECT c.id AS claim_id, a.id AS employer_assignment_id,
           COUNT(*) OVER (PARTITION BY c.id) AS match_count
    FROM claims c
    JOIN member_employer_assignments a ON a.member_id = c.member_id
    WHERE c.employer_assignment_id IS NULL
      AND c.service_date IS NOT NULL
      AND a.assignment_start_date <= c.service_date
      AND (a.assignment_end_date IS NULL OR a.assignment_end_date > c.service_date)
)
UPDATE claims c
   SET employer_assignment_id = r.employer_assignment_id
  FROM resolved r
 WHERE c.id = r.claim_id
   AND r.match_count = 1;

-- Log every claim that still has a gap. Deliberately never guesses: a claim
-- with zero or multiple candidate assignments at its serviceDate is logged,
-- not defaulted.
INSERT INTO claims_historical_context_backfill_gaps (claim_id, missing, reason)
SELECT c.id, 'policy_id',
       'No single agreeing claim_line_limit_snapshots.policy_id and no single member_policy_assignments row covers service_date'
FROM claims c
WHERE c.policy_id IS NULL
ON CONFLICT (claim_id, missing) DO NOTHING;

-- A claim can have policy_id filled (snapshots agreed on the policy) while
-- policy_assignment_id stays NULL (snapshots disagreed on WHICH enrollment
-- period, or no snapshot named one at all). That row would never surface
-- above since policy_id is non-null there -- log it separately so it is not
-- missed before policy_assignment_id also goes NOT NULL.
INSERT INTO claims_historical_context_backfill_gaps (claim_id, missing, reason)
SELECT c.id, 'policy_assignment_id',
       'policy_id resolved but no single member_policy_assignments row could be attributed'
FROM claims c
WHERE c.policy_id IS NOT NULL
  AND c.policy_assignment_id IS NULL
ON CONFLICT (claim_id, missing) DO NOTHING;

INSERT INTO claims_historical_context_backfill_gaps (claim_id, missing, reason)
SELECT c.id, 'employer_assignment_id',
       'No single member_employer_assignments row covers service_date'
FROM claims c
WHERE c.employer_assignment_id IS NULL
ON CONFLICT (claim_id, missing) DO NOTHING;

-- ============================================================
-- Immutability: once a claim leaves DRAFT/NEEDS_CORRECTION, these three
-- columns are historical fact and must never change again -- mirroring the
-- financial-snapshot-immutable-after-APPROVED rule ClaimService already
-- enforces in application code for approvedAmount/netProviderAmount/etc.
-- ============================================================
CREATE OR REPLACE FUNCTION claims_historical_context_guard()
RETURNS trigger AS $$
BEGIN
    IF OLD.status NOT IN ('DRAFT', 'NEEDS_CORRECTION') THEN
        IF NEW.policy_id IS DISTINCT FROM OLD.policy_id
           OR NEW.policy_assignment_id IS DISTINCT FROM OLD.policy_assignment_id
           OR NEW.employer_assignment_id IS DISTINCT FROM OLD.employer_assignment_id THEN
            RAISE EXCEPTION 'claims: policy_id/policy_assignment_id/employer_assignment_id are immutable once the claim leaves DRAFT/NEEDS_CORRECTION (claim %)', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_claims_historical_context_guard ON claims;
CREATE TRIGGER trg_claims_historical_context_guard
BEFORE UPDATE ON claims
FOR EACH ROW EXECUTE FUNCTION claims_historical_context_guard();
