-- ============================================================================
-- V13: Core alignment for benefit_policy_rules table with BenefitPolicyRule entity
-- ============================================================================

ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS benefit_policy_id BIGINT;
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS medical_category_id BIGINT;
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS medical_service_id BIGINT;
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS coverage_percent INTEGER;
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS amount_limit NUMERIC(15,2);
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS times_limit INTEGER;
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS requires_pre_approval BOOLEAN;
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS notes VARCHAR(500);
ALTER TABLE benefit_policy_rules ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Backfill mapped columns from legacy schema where possible
UPDATE benefit_policy_rules
SET benefit_policy_id = COALESCE(benefit_policy_id, policy_id)
WHERE benefit_policy_id IS NULL;

UPDATE benefit_policy_rules
SET medical_service_id = COALESCE(medical_service_id, canonical_service_id)
WHERE medical_service_id IS NULL;

UPDATE benefit_policy_rules
SET coverage_percent = COALESCE(coverage_percent, ROUND(coverage_percentage)::INTEGER)
WHERE coverage_percent IS NULL AND coverage_percentage IS NOT NULL;

UPDATE benefit_policy_rules
SET amount_limit = COALESCE(amount_limit, max_amount_per_session)
WHERE amount_limit IS NULL AND max_amount_per_session IS NOT NULL;

UPDATE benefit_policy_rules
SET times_limit = COALESCE(times_limit, max_sessions_per_year)
WHERE times_limit IS NULL AND max_sessions_per_year IS NOT NULL;

UPDATE benefit_policy_rules
SET requires_pre_approval = COALESCE(requires_pre_approval, requires_preauth, false)
WHERE requires_pre_approval IS NULL;

UPDATE benefit_policy_rules
SET updated_at = COALESCE(updated_at, created_at)
WHERE updated_at IS NULL;

ALTER TABLE benefit_policy_rules ALTER COLUMN requires_pre_approval SET DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_bpr_policy ON benefit_policy_rules(benefit_policy_id);
CREATE INDEX IF NOT EXISTS idx_bpr_category ON benefit_policy_rules(medical_category_id);
CREATE INDEX IF NOT EXISTS idx_bpr_service ON benefit_policy_rules(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_bpr_active ON benefit_policy_rules(active);

-- ============================================================================
-- Migration Complete: V13
-- ============================================================================
