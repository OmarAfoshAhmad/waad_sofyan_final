DROP INDEX IF EXISTS idx_users_username;
DROP INDEX IF EXISTS idx_users_email;
DROP INDEX IF EXISTS idx_users_enabled;

ALTER TABLE users
    DROP COLUMN IF EXISTS enabled,
    DROP COLUMN IF EXISTS account_non_expired,
    DROP COLUMN IF EXISTS account_non_locked,
    DROP COLUMN IF EXISTS credentials_non_expired,
    DROP COLUMN IF EXISTS identity_verified,
    DROP COLUMN IF EXISTS identity_verified_at,
    DROP COLUMN IF EXISTS identity_verified_by,
    DROP COLUMN IF EXISTS company_id,
    DROP COLUMN IF EXISTS last_login,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS updated_by;
