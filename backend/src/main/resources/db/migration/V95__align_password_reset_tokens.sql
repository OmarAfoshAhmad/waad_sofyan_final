ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS otp VARCHAR(255);

UPDATE password_reset_tokens prt
SET email = COALESCE(
    prt.email,
    (SELECT u.email FROM users u WHERE u.id = prt.user_id),
    'legacy-reset-' || prt.id || '@invalid.local'
)
WHERE prt.email IS NULL;

ALTER TABLE password_reset_tokens
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN user_id DROP NOT NULL,
    ALTER COLUMN token DROP NOT NULL,
    ALTER COLUMN expiry_date DROP NOT NULL,
    ALTER COLUMN expires_at DROP NOT NULL,
    ALTER COLUMN used SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_email
    ON password_reset_tokens(email);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_otp
    ON password_reset_tokens(otp)
    WHERE otp IS NOT NULL;
