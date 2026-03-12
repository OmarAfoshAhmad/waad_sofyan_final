-- ============================================================
-- Table: user_login_attempts
-- Depends on: (none – no FK to users for performance)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_login_attempts (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(255) NOT NULL,
    ip_address     VARCHAR(50),
    user_agent     TEXT,

    attempt_result VARCHAR(20) DEFAULT 'SUCCESS'
        CHECK (attempt_result IN ('SUCCESS','FAILURE','LOCKED')),
    failure_reason VARCHAR(500),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    user_id        BIGINT,
    success        BOOLEAN DEFAULT false,
    failed_reason  VARCHAR(255),
    attempted_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username            ON user_login_attempts(username);
CREATE INDEX IF NOT EXISTS idx_login_attempts_created             ON user_login_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_result              ON user_login_attempts(attempt_result);
CREATE INDEX IF NOT EXISTS idx_login_attempts_user_id_attempted   ON user_login_attempts(user_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_success_attempted   ON user_login_attempts(success, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed              ON user_login_attempts(username, attempted_at DESC)
    WHERE success = false;
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed_window       ON user_login_attempts(username, attempted_at DESC)
    WHERE success = false;
