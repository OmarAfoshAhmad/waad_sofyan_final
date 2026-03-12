-- ============================================================
-- Table: user_audit_log
-- Depends on: users
-- ============================================================
CREATE TABLE IF NOT EXISTS user_audit_log (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT,
    username           VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    action_type        VARCHAR(100) NOT NULL DEFAULT 'GENERIC',
    action_description TEXT,
    action             VARCHAR(100),
    details            TEXT,
    performed_by       BIGINT,
    entity_type        VARCHAR(100),
    entity_id          BIGINT,
    old_value          TEXT,
    new_value          TEXT,
    ip_address         VARCHAR(50),
    user_agent         TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_user              ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_username          ON user_audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_action_type       ON user_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity            ON user_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_created           ON user_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_audit_action_created ON user_audit_log(action_type, created_at DESC);
