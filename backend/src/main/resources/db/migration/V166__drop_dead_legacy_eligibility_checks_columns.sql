-- ============================================================
-- V166: eligibility_checks had two generations of the same
-- columns left over from V16's original design, never cleaned up:
--   is_eligible (legacy) alongside eligible (current, entity-mapped)
--   check_date  (legacy) alongside check_timestamp (current, entity-mapped)
--   eligibility_reason / coverage_status / checked_by (legacy, unmapped)
-- EligibilityCheck.java has never mapped is_eligible, check_date,
-- eligibility_reason, coverage_status, or checked_by -- so no insert
-- through the application has ever populated them. Because is_eligible
-- and eligible are both NOT NULL, every insert into this table has
-- always failed against a real, freshly-migrated Postgres database
-- (masked until now by tests that never exercised this table against
-- real Postgres). Dropping the dead legacy columns; the rest of the
-- row is already fully covered by the current column set.
-- ============================================================

ALTER TABLE eligibility_checks
    DROP COLUMN IF EXISTS is_eligible,
    DROP COLUMN IF EXISTS check_date,
    DROP COLUMN IF EXISTS eligibility_reason,
    DROP COLUMN IF EXISTS coverage_status,
    DROP COLUMN IF EXISTS checked_by;
