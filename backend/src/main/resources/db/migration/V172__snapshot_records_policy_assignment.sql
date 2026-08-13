-- ============================================================
-- V172: the claim limit snapshot records WHICH ASSIGNMENT the policy was
-- applied through, not only which policy.
--
-- claim_line_limit_snapshots already captured policy_id and source_version,
-- which is enough to reconstruct "what the limits were". It is NOT enough to
-- reconstruct "why this member was on that policy": the same logical policy
-- can apply to a member across two SEPARATE assignment periods (assigned,
-- ended, later re-assigned). Two claims resolving to the same policy_id may
-- therefore belong to different coverage periods, and nothing in the
-- snapshot distinguished them.
--
-- No FK to member_policy_assignments: that table is append-only with an
-- identity snapshot and no member FK (V171), so a snapshot referencing it
-- must not be able to block or be blocked by anything there. This column is
-- an immutable identifier, in the same spirit.
-- ============================================================

ALTER TABLE claim_line_limit_snapshots
    ADD COLUMN IF NOT EXISTS member_policy_assignment_id BIGINT;

COMMENT ON COLUMN claim_line_limit_snapshots.member_policy_assignment_id IS
    'The member_policy_assignments row the policy was resolved through; identifier snapshot, deliberately no FK';

CREATE INDEX IF NOT EXISTS idx_claim_limit_snapshot_assignment
    ON claim_line_limit_snapshots(member_policy_assignment_id)
    WHERE member_policy_assignment_id IS NOT NULL;
