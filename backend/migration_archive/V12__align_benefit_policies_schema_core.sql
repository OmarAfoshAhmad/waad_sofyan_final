-- ============================================================================
-- V12: Core alignment for benefit_policies table with BenefitPolicy entity
-- ============================================================================

ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS default_coverage_percent INTEGER;
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS per_member_limit NUMERIC(15,2);
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS per_family_limit NUMERIC(15,2);
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS default_waiting_period_days INTEGER;
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS notes VARCHAR(1000);
ALTER TABLE benefit_policies ADD COLUMN IF NOT EXISTS covered_members_count INTEGER;

UPDATE benefit_policies
SET name = COALESCE(name, policy_name)
WHERE name IS NULL;

UPDATE benefit_policies
SET start_date = COALESCE(start_date, effective_date)
WHERE start_date IS NULL;

UPDATE benefit_policies
SET end_date = COALESCE(end_date, expiry_date)
WHERE end_date IS NULL;

UPDATE benefit_policies
SET default_coverage_percent = COALESCE(default_coverage_percent, 80)
WHERE default_coverage_percent IS NULL;

UPDATE benefit_policies
SET default_waiting_period_days = COALESCE(default_waiting_period_days, 0)
WHERE default_waiting_period_days IS NULL;

UPDATE benefit_policies
SET status = COALESCE(status, CASE WHEN active THEN 'ACTIVE' ELSE 'DRAFT' END)
WHERE status IS NULL;

UPDATE benefit_policies
SET covered_members_count = COALESCE(covered_members_count, 0)
WHERE covered_members_count IS NULL;

-- add safe defaults (do not force NOT NULL to keep legacy compatibility)
ALTER TABLE benefit_policies ALTER COLUMN default_coverage_percent SET DEFAULT 80;
ALTER TABLE benefit_policies ALTER COLUMN default_waiting_period_days SET DEFAULT 0;
ALTER TABLE benefit_policies ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE benefit_policies ALTER COLUMN covered_members_count SET DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_benefit_policy_start_date ON benefit_policies(start_date);
CREATE INDEX IF NOT EXISTS idx_benefit_policy_end_date ON benefit_policies(end_date);

-- ============================================================================
-- Migration Complete: V12
-- ============================================================================
