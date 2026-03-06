-- Migration V20260305_01: Add missing fields for backlog claims and rejections
-- Sequenced after V20260304_02 to avoid Flyway "out of order" validation errors

-- 1. Add complaint column to visits table
ALTER TABLE visits ADD COLUMN IF NOT EXISTS complaint TEXT;

-- 2. Add rejected column to claim_lines table
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS rejected BOOLEAN DEFAULT FALSE;

-- 3. Add index for rejected lines to help in reports
CREATE INDEX IF NOT EXISTS idx_claim_lines_rejected ON claim_lines(rejected) WHERE rejected = TRUE;
