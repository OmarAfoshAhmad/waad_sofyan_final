-- ============================================================
-- V105: Add coverage category context to claims
-- ============================================================

-- 1. Add category context fields to claims table
ALTER TABLE claims 
ADD COLUMN manual_category_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN primary_category_code VARCHAR(50);

-- 2. Add applied category fields to claim_lines table
ALTER TABLE claim_lines
ADD COLUMN applied_category_id BIGINT,
ADD COLUMN applied_category_name VARCHAR(200);

-- 3. Update existing records (optional, but good for consistency)
UPDATE claims SET manual_category_enabled = FALSE WHERE manual_category_enabled IS NULL;
