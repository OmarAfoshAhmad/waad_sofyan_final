-- ============================================================
-- Table: password_reset_tokens
-- Depends on: users
-- ============================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id           BIGSERIAL PRIMARY KEY,
    token        VARCHAR(255) NOT NULL UNIQUE,
    user_id      BIGINT NOT NULL,
    expiry_date  TIMESTAMP NOT NULL,
    expires_at   TIMESTAMP NOT NULL,
    used         BOOLEAN DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_tokens_user       ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_tokens_token      ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_tokens_expiry     ON password_reset_tokens(expiry_date);
CREATE INDEX IF NOT EXISTS idx_password_tokens_expires_at ON password_reset_tokens(expires_at);
