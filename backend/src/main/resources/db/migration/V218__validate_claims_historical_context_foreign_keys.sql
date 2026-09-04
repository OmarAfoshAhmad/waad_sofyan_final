-- V217 added policy_id/policy_assignment_id/employer_assignment_id to claims
-- with their FKs as NOT VALID, because the backfill it ran in the same
-- migration could not resolve every existing claim without guessing. This
-- migration validates those FKs now that the backfill has run and its gap
-- report (claims_historical_context_backfill_gaps) has been reviewed.
--
-- VALIDATE CONSTRAINT only checks rows that currently hold a value; it does
-- not require every row to have one. So it is safe to run before every gap
-- is closed -- and it must be, because closing the last gaps found in
-- waad_production_review_20260903 (2026-09-03) needs a human, per-claim
-- judgment call (docs/testing/CLAIMS_POLICY_SNAPSHOT_BACKFILL_GAPS.md), not
-- a mechanical migration.
ALTER TABLE claims VALIDATE CONSTRAINT fk_claims_policy;
ALTER TABLE claims VALIDATE CONSTRAINT fk_claims_policy_assignment;
ALTER TABLE claims VALIDATE CONSTRAINT fk_claims_employer_assignment;

-- Deliberately NOT done here: ALTER COLUMN ... SET NOT NULL on any of the
-- three columns. claims_historical_context_backfill_gaps is not empty as of
-- this migration -- some existing claims' resolved policy/employer-assignment
-- genuinely could not be identified without guessing, and forcing NOT NULL
-- now would mean either the migration fails outright, or someone quietly
-- invented a value to make it pass. Both are worse than an honestly nullable
-- column. NOT NULL lands in its own migration once
-- claims_historical_context_backfill_gaps is empty, or every remaining row
-- has been resolved by an explicit, reviewed, per-claim correction.
DO $$
DECLARE remaining BIGINT;
BEGIN
    SELECT count(*) INTO remaining FROM claims_historical_context_backfill_gaps;
    IF remaining > 0 THEN
        RAISE NOTICE 'V218: % claims_historical_context_backfill_gaps row(s) remain open; NOT NULL is deferred, not skipped', remaining;
    END IF;
END $$;
