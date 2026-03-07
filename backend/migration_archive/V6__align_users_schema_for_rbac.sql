-- ============================================================================
-- V6: Align Users Schema for RBAC Runtime Model
-- ============================================================================
-- Purpose:
--   Align `users` table with current User JPA entity so startup initializers,
--   authentication, and lockout features can persist correctly.
--
-- Safe/Idempotent:
--   Uses IF NOT EXISTS where possible.
-- ============================================================================

-- Add missing runtime columns expected by User entity
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_id BIGINT;

-- Backfill new activity/login columns from legacy fields if present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'enabled'
    ) THEN
        EXECUTE 'UPDATE users
                 SET is_active = enabled
                 WHERE enabled IS NOT NULL
                   AND (is_active IS NULL OR is_active <> enabled)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'last_login'
    ) THEN
        EXECUTE 'UPDATE users
                 SET last_login_at = last_login
                 WHERE last_login IS NOT NULL
                   AND last_login_at IS NULL';
    END IF;
END $$;

-- Ensure primary key generation aligns with sequence-based IDs
ALTER TABLE users ALTER COLUMN id SET DEFAULT nextval('user_seq');

-- Keep sequence ahead of existing data
SELECT setval('user_seq', GREATEST(COALESCE((SELECT MAX(id) FROM users), 0) + 1, 1), false);

-- Helpful index for active-user lookups
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);

-- ============================================================================
-- Migration Complete: V6
-- ============================================================================
