-- ============================================================================
-- V15: Core alignment for members table with Member entity runtime columns
-- ============================================================================

ALTER TABLE members ADD COLUMN IF NOT EXISTS barcode VARCHAR(100);
ALTER TABLE members ADD COLUMN IF NOT EXISTS benefit_policy_id BIGINT;
ALTER TABLE members ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS blocked_reason VARCHAR(500);
ALTER TABLE members ADD COLUMN IF NOT EXISTS card_activated_at TIMESTAMP;
ALTER TABLE members ADD COLUMN IF NOT EXISTS card_number VARCHAR(50);
ALTER TABLE members ADD COLUMN IF NOT EXISTS card_status VARCHAR(30);
ALTER TABLE members ADD COLUMN IF NOT EXISTS civil_id VARCHAR(50);
ALTER TABLE members ADD COLUMN IF NOT EXISTS eligibility_status VARCHAR(30);
ALTER TABLE members ADD COLUMN IF NOT EXISTS eligibility_updated_at TIMESTAMP;
ALTER TABLE members ADD COLUMN IF NOT EXISTS emergency_notes TEXT;
ALTER TABLE members ADD COLUMN IF NOT EXISTS employee_number VARCHAR(100);
ALTER TABLE members ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS is_smart_card BOOLEAN;
ALTER TABLE members ADD COLUMN IF NOT EXISTS is_urgent BOOLEAN;
ALTER TABLE members ADD COLUMN IF NOT EXISTS is_vip BOOLEAN;
ALTER TABLE members ADD COLUMN IF NOT EXISTS join_date DATE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS marital_status VARCHAR(20);
ALTER TABLE members ADD COLUMN IF NOT EXISTS national_number VARCHAR(50);
ALTER TABLE members ADD COLUMN IF NOT EXISTS nationality VARCHAR(100);
ALTER TABLE members ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE members ADD COLUMN IF NOT EXISTS occupation VARCHAR(100);
ALTER TABLE members ADD COLUMN IF NOT EXISTS parent_id BIGINT;
ALTER TABLE members ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
ALTER TABLE members ADD COLUMN IF NOT EXISTS profile_photo_path VARCHAR(500);
ALTER TABLE members ADD COLUMN IF NOT EXISTS relationship VARCHAR(50);
ALTER TABLE members ADD COLUMN IF NOT EXISTS start_date DATE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE members ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
ALTER TABLE members ADD COLUMN IF NOT EXISTS version BIGINT;

-- Backfill from legacy columns where possible
UPDATE members SET card_number = member_card_id WHERE card_number IS NULL AND member_card_id IS NOT NULL;
UPDATE members SET birth_date = date_of_birth WHERE birth_date IS NULL AND date_of_birth IS NOT NULL;
UPDATE members SET national_number = national_id WHERE national_number IS NULL AND national_id IS NOT NULL;
UPDATE members SET employee_number = employee_id WHERE employee_number IS NULL AND employee_id IS NOT NULL;
UPDATE members SET start_date = coverage_start_date WHERE start_date IS NULL AND coverage_start_date IS NOT NULL;
UPDATE members SET end_date = coverage_end_date WHERE end_date IS NULL AND coverage_end_date IS NOT NULL;
UPDATE members SET relationship = relation_to_employee WHERE relationship IS NULL AND relation_to_employee IS NOT NULL;

-- Safe defaults for runtime paths
UPDATE members SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END WHERE status IS NULL;
UPDATE members SET version = 0 WHERE version IS NULL;
UPDATE members SET is_smart_card = false WHERE is_smart_card IS NULL;
UPDATE members SET is_urgent = false WHERE is_urgent IS NULL;
UPDATE members SET is_vip = false WHERE is_vip IS NULL;

ALTER TABLE members ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE members ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE members ALTER COLUMN is_smart_card SET DEFAULT false;
ALTER TABLE members ALTER COLUMN is_urgent SET DEFAULT false;
ALTER TABLE members ALTER COLUMN is_vip SET DEFAULT false;

-- FK/indexes needed by current query patterns
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_members_parent') THEN
        ALTER TABLE members
            ADD CONSTRAINT fk_members_parent
            FOREIGN KEY (parent_id) REFERENCES members(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_members_benefit_policy_v15') THEN
        ALTER TABLE members
            ADD CONSTRAINT fk_members_benefit_policy_v15
            FOREIGN KEY (benefit_policy_id) REFERENCES benefit_policies(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_members_status ON members(status);
CREATE INDEX IF NOT EXISTS idx_members_parent_id ON members(parent_id);
CREATE INDEX IF NOT EXISTS idx_members_barcode ON members(barcode);
CREATE INDEX IF NOT EXISTS idx_members_card_number ON members(card_number);
CREATE INDEX IF NOT EXISTS idx_members_civil_id ON members(civil_id);
CREATE INDEX IF NOT EXISTS idx_members_benefit_policy_id_v15 ON members(benefit_policy_id);

-- ============================================================================
-- Migration Complete: V15
-- ============================================================================
