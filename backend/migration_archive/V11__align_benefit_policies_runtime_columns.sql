-- ============================================================================
-- V11: Align benefit_policies table with runtime entity columns
-- ============================================================================

ALTER TABLE benefit_policies
    ADD COLUMN IF NOT EXISTS covered_members_count INTEGER;

UPDATE benefit_policies
SET covered_members_count = 0
WHERE covered_members_count IS NULL;

ALTER TABLE benefit_policies
    ALTER COLUMN covered_members_count SET DEFAULT 0;

-- ============================================================================
-- Migration Complete: V11
-- ============================================================================
