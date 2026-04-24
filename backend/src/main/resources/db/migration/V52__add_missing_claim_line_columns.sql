-- Migration: Add missing columns to claim_lines
-- V52: Fix for "manual_refusal_reason" and other potentially missing audit fields

ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS manual_refusal_reason VARCHAR(500);

-- Financial audit fields (Ensure they exist if V20 or V51 didn't cover some environments)
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS requested_unit_price NUMERIC(15, 2);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS approved_unit_price NUMERIC(15, 2);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS requested_quantity   INTEGER;
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS approved_quantity    INTEGER;

-- Add version column if missing (for optimistic locking added in hardening phase)
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

COMMENT ON COLUMN claim_lines.manual_refusal_reason IS 'Reason for manual refusal or partial rejection of a claim line';
