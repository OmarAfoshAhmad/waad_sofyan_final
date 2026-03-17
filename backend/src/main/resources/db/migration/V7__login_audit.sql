-- ============================================================
-- V7: جداول تسجيل الدخول والتدقيق
-- ============================================================

CREATE TABLE IF NOT EXISTS user_login_attempts (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(50),
    user_agent      TEXT,

    attempt_result  VARCHAR(20) DEFAULT 'SUCCESS'
        CHECK (attempt_result IN ('SUCCESS','FAILURE','LOCKED')),
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    user_id         BIGINT,
    success         BOOLEAN DEFAULT false,
    failed_reason   VARCHAR(255),
    attempted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username              ON user_login_attempts(username);
CREATE INDEX IF NOT EXISTS idx_login_attempts_created               ON user_login_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_result                ON user_login_attempts(attempt_result);
CREATE INDEX IF NOT EXISTS idx_login_attempts_user_id_attempted     ON user_login_attempts(user_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_success_attempted     ON user_login_attempts(success, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed                ON user_login_attempts(username, attempted_at DESC) WHERE success = false;
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed_window         ON user_login_attempts(username, attempted_at DESC) WHERE success = false;

CREATE TABLE IF NOT EXISTS user_audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT,
    username            VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    action_type         VARCHAR(100) NOT NULL DEFAULT 'GENERIC',
    action_description  TEXT,
    action              VARCHAR(100),
    details             TEXT,
    performed_by        BIGINT,
    entity_type         VARCHAR(100),
    entity_id           BIGINT,
    old_value           TEXT,
    new_value           TEXT,
    ip_address          VARCHAR(50),
    user_agent          TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_user               ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_username            ON user_audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_action_type         ON user_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity              ON user_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_created             ON user_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_audit_action_created ON user_audit_log(action_type, created_at DESC);
