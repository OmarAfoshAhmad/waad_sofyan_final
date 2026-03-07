-- V38: Align password_reset_tokens table for both auth and RBAC token entities

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS otp VARCHAR(255);

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS expiry_time TIMESTAMP;

-- Backfill to satisfy NOT NULL required by AuthPasswordResetToken
UPDATE password_reset_tokens
SET email = COALESCE(email, CONCAT('user-', user_id, '@placeholder.local'))
WHERE email IS NULL;

UPDATE password_reset_tokens
SET otp = COALESCE(otp, token)
WHERE otp IS NULL;

UPDATE password_reset_tokens
SET expiry_time = COALESCE(expiry_time, expires_at)
WHERE expiry_time IS NULL;

ALTER TABLE password_reset_tokens
    ALTER COLUMN email SET NOT NULL;

ALTER TABLE password_reset_tokens
    ALTER COLUMN otp SET NOT NULL;

ALTER TABLE password_reset_tokens
    ALTER COLUMN expiry_time SET NOT NULL;
