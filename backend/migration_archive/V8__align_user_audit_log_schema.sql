-- ============================================================================
-- V8: Align user_audit_log schema with runtime entity mapping
-- ============================================================================
-- Problem fixed:
--   Entity writes columns: action, details, performed_by, user_id, created_at
--   Baseline table has:    action_type, action_description, ... with username NOT NULL
--
-- Strategy:
--   Add runtime columns expected by current code while preserving legacy fields.
--   Add defaults/trigger to keep both representations in sync.
-- ============================================================================

-- 1) Add columns expected by current JPA entity
ALTER TABLE user_audit_log ADD COLUMN IF NOT EXISTS action VARCHAR(100);
ALTER TABLE user_audit_log ADD COLUMN IF NOT EXISTS details TEXT;
ALTER TABLE user_audit_log ADD COLUMN IF NOT EXISTS performed_by BIGINT;

-- 2) Ensure legacy required columns do not block new inserts
ALTER TABLE user_audit_log ALTER COLUMN username SET DEFAULT 'SYSTEM';
ALTER TABLE user_audit_log ALTER COLUMN action_type SET DEFAULT 'GENERIC';

-- 3) Backfill runtime columns from legacy columns
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_audit_log' AND column_name = 'action_type'
    ) THEN
        EXECUTE 'UPDATE user_audit_log
                 SET action = action_type
                 WHERE action IS NULL AND action_type IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_audit_log' AND column_name = 'action_description'
    ) THEN
        EXECUTE 'UPDATE user_audit_log
                 SET details = action_description
                 WHERE details IS NULL AND action_description IS NOT NULL';
    END IF;
END $$;

-- 4) Keep legacy and runtime columns synchronized
CREATE OR REPLACE FUNCTION sync_user_audit_log_columns()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.action IS NULL AND NEW.action_type IS NOT NULL THEN
        NEW.action := NEW.action_type;
    ELSIF NEW.action IS NOT NULL THEN
        NEW.action_type := NEW.action;
    END IF;

    IF NEW.details IS NULL AND NEW.action_description IS NOT NULL THEN
        NEW.details := NEW.action_description;
    ELSIF NEW.details IS NOT NULL THEN
        NEW.action_description := NEW.details;
    END IF;

    IF NEW.username IS NULL OR NEW.username = '' THEN
        NEW.username := 'SYSTEM';
    END IF;

    IF NEW.action_type IS NULL OR NEW.action_type = '' THEN
        NEW.action_type := 'GENERIC';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_user_audit_log_columns ON user_audit_log;
CREATE TRIGGER trg_sync_user_audit_log_columns
BEFORE INSERT OR UPDATE ON user_audit_log
FOR EACH ROW
EXECUTE FUNCTION sync_user_audit_log_columns();

-- 5) Useful index for current query pattern
CREATE INDEX IF NOT EXISTS idx_user_audit_action_created ON user_audit_log(action, created_at DESC);

-- ============================================================================
-- Migration Complete: V8
-- ============================================================================
