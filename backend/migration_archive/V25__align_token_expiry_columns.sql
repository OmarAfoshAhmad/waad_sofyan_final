-- V25: Align token tables with RBAC token entities (expires_at)

ALTER TABLE email_verification_tokens
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

UPDATE email_verification_tokens
SET expires_at = COALESCE(expires_at, expiry_date)
WHERE expires_at IS NULL;

ALTER TABLE email_verification_tokens
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_email_tokens_expires_at ON email_verification_tokens(expires_at);

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

UPDATE password_reset_tokens
SET expires_at = COALESCE(expires_at, expiry_date)
WHERE expires_at IS NULL;

ALTER TABLE password_reset_tokens
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_password_tokens_expires_at ON password_reset_tokens(expires_at);
