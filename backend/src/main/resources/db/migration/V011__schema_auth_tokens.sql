-- ============================================================
-- V011: Authentication tokens (email verification + password reset)
-- ============================================================
-- Depends on: V010 (users)

-- ----------------------------------------------------------
-- SECTION 1: Email verification tokens
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    verified    BOOLEAN DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_email_verify_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_email_tokens_user      ON email_verification_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_email_tokens_token     ON email_verification_tokens(token);
CREATE INDEX IF NOT EXISTS idx_email_tokens_expiry    ON email_verification_tokens(expiry_date);
CREATE INDEX IF NOT EXISTS idx_email_tokens_expires_at ON email_verification_tokens(expires_at);

-- ----------------------------------------------------------
-- SECTION 2: Password reset tokens
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    used        BOOLEAN DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_tokens_user      ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_tokens_token     ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_tokens_expiry    ON password_reset_tokens(expiry_date);
CREATE INDEX IF NOT EXISTS idx_password_tokens_expires_at ON password_reset_tokens(expires_at);
