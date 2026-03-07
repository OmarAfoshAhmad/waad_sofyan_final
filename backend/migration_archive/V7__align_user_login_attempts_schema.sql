-- ============================================================================
-- V7: Align user_login_attempts schema with runtime entity mapping
-- ============================================================================
-- Problem fixed:
--   Entity writes columns: attempted_at, failed_reason, success, user_id
--   Baseline table has:    created_at, failure_reason, attempt_result
--
-- Strategy:
--   Add runtime columns expected by code while keeping legacy columns.
--   Add defaults/trigger so legacy NOT NULL attempt_result remains valid.
-- ============================================================================

-- 1) Add columns expected by current JPA entity
ALTER TABLE user_login_attempts ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE user_login_attempts ADD COLUMN IF NOT EXISTS success BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE user_login_attempts ADD COLUMN IF NOT EXISTS failed_reason VARCHAR(255);
ALTER TABLE user_login_attempts ADD COLUMN IF NOT EXISTS attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 2) Backfill from legacy columns (if present)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_login_attempts' AND column_name = 'failure_reason'
    ) THEN
        EXECUTE 'UPDATE user_login_attempts
                 SET failed_reason = failure_reason
                 WHERE failed_reason IS NULL AND failure_reason IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_login_attempts' AND column_name = 'created_at'
    ) THEN
        EXECUTE 'UPDATE user_login_attempts
                 SET attempted_at = created_at
                 WHERE attempted_at IS NULL AND created_at IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_login_attempts' AND column_name = 'attempt_result'
    ) THEN
        EXECUTE 'UPDATE user_login_attempts
                 SET success = CASE WHEN attempt_result = ''SUCCESS'' THEN true ELSE false END
                 WHERE success IS NULL';
    END IF;
END $$;

-- 3) Ensure legacy required column does not block new inserts
ALTER TABLE user_login_attempts ALTER COLUMN attempt_result SET DEFAULT 'SUCCESS';

-- 4) Keep attempt_result consistent with success for legacy reporting
CREATE OR REPLACE FUNCTION sync_login_attempt_result()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.success IS NULL THEN
        NEW.success := false;
    END IF;

    NEW.attempt_result := CASE
        WHEN NEW.success = true THEN 'SUCCESS'
        ELSE 'FAILURE'
    END;

    IF NEW.attempted_at IS NULL THEN
        NEW.attempted_at := CURRENT_TIMESTAMP;
    END IF;

    IF NEW.created_at IS NULL THEN
        NEW.created_at := NEW.attempted_at;
    END IF;

    IF NEW.failed_reason IS NULL AND NEW.failure_reason IS NOT NULL THEN
        NEW.failed_reason := NEW.failure_reason;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_login_attempt_result ON user_login_attempts;
CREATE TRIGGER trg_sync_login_attempt_result
BEFORE INSERT OR UPDATE ON user_login_attempts
FOR EACH ROW
EXECUTE FUNCTION sync_login_attempt_result();

-- 5) Useful indexes for current query patterns
CREATE INDEX IF NOT EXISTS idx_login_attempts_user_id_attempted ON user_login_attempts(user_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_username_attempted ON user_login_attempts(username, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_success_attempted ON user_login_attempts(success, attempted_at DESC);

-- ============================================================================
-- Migration Complete: V7
-- ============================================================================
