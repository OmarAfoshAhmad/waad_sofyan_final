-- ============================================================
-- V097: Backfill missing columns for claim_lines / claims
--
-- Fixes runtime JDBC errors like:
-- "column ... approved_quantity does not exist"
-- ============================================================

-- 1) claims.version (optimistic locking field in Claim entity)
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2) claim_lines.version (optimistic locking field in ClaimLine entity)
ALTER TABLE claim_lines
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 3) claim_lines review/audit fields present in ClaimLine entity
ALTER TABLE claim_lines
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS rejection_reason_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reviewer_notes TEXT,
    ADD COLUMN IF NOT EXISTS rejected BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS requested_unit_price NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS approved_unit_price NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS requested_quantity INTEGER,
    ADD COLUMN IF NOT EXISTS approved_quantity INTEGER;

-- Optional hygiene for old rows: keep booleans non-null
UPDATE claim_lines
SET rejected = false
WHERE rejected IS NULL;
