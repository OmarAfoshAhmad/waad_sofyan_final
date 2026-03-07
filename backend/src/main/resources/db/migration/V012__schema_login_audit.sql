-- ============================================================
-- V012: Login attempts and user audit log
-- ============================================================
-- Depends on: V010 (users)

-- ----------------------------------------------------------
-- SECTION 1: Login attempt tracking (security)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_login_attempts (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(50),
    user_agent      TEXT,

    -- Legacy result columns
    attempt_result  VARCHAR(20) DEFAULT 'SUCCESS'
        CHECK (attempt_result IN ('SUCCESS','FAILURE','LOCKED')),
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Runtime columns (synced with above via trigger)
    user_id         BIGINT,
    success         BOOLEAN DEFAULT false,
    failed_reason   VARCHAR(255),
    attempted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Trigger keeps attempt_result / success / created_at / attempted_at in sync
CREATE OR REPLACE FUNCTION trg_sync_login_attempt_result_fn()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.attempt_result IS NOT NULL AND NEW.success IS NULL THEN
        NEW.success := (NEW.attempt_result = 'SUCCESS');
    ELSIF NEW.success IS NOT NULL AND NEW.attempt_result IS NULL THEN
        NEW.attempt_result := CASE WHEN NEW.success THEN 'SUCCESS' ELSE 'FAILURE' END;
    END IF;
    IF NEW.attempted_at IS NULL THEN NEW.attempted_at := COALESCE(NEW.created_at, CURRENT_TIMESTAMP); END IF;
    IF NEW.created_at   IS NULL THEN NEW.created_at   := NEW.attempted_at; END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_login_attempt_result ON user_login_attempts;
CREATE TRIGGER trg_sync_login_attempt_result
    BEFORE INSERT OR UPDATE ON user_login_attempts
    FOR EACH ROW EXECUTE FUNCTION trg_sync_login_attempt_result_fn();

CREATE INDEX IF NOT EXISTS idx_login_attempts_username          ON user_login_attempts(username);
CREATE INDEX IF NOT EXISTS idx_login_attempts_created           ON user_login_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_result            ON user_login_attempts(attempt_result);
CREATE INDEX IF NOT EXISTS idx_login_attempts_user_id_attempted ON user_login_attempts(user_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_success_attempted ON user_login_attempts(success, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed            ON user_login_attempts(username, attempted_at DESC)
    WHERE success = false;

-- ----------------------------------------------------------
-- SECTION 2: User audit log (generic entity change history)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT,

    -- Legacy columns
    username            VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    action_type         VARCHAR(100) NOT NULL DEFAULT 'GENERIC',
    action_description  TEXT,

    -- Runtime columns (synced via trigger)
    action          VARCHAR(100),
    details         TEXT,
    performed_by    BIGINT,

    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    old_value       TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE OR REPLACE FUNCTION trg_sync_user_audit_log_fn()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.action IS NULL THEN NEW.action := NEW.action_type; END IF;
    IF NEW.action_type IS NULL THEN NEW.action_type := NEW.action; END IF;
    IF NEW.details IS NULL THEN NEW.details := NEW.action_description; END IF;
    IF NEW.action_description IS NULL THEN NEW.action_description := NEW.details; END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_user_audit_log ON user_audit_log;
CREATE TRIGGER trg_sync_user_audit_log
    BEFORE INSERT OR UPDATE ON user_audit_log
    FOR EACH ROW EXECUTE FUNCTION trg_sync_user_audit_log_fn();

CREATE INDEX IF NOT EXISTS idx_audit_user         ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_username     ON user_audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_action_type  ON user_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity       ON user_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_created      ON user_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_audit_action_created ON user_audit_log(action_type, created_at DESC);
