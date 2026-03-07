-- ============================================================================
-- V81: Final Authorization Lockdown — CHECK Constraint on user_type
-- ============================================================================
-- WHAT: Adds a CHECK constraint to enforce exactly 7 valid roles.
--       Normalizes any remaining legacy role values.
--       Drops the deprecated company_id column.
--
-- WHY: Final lockdown after removing all dynamic RBAC artifacts in V80.
--      Prevents invalid role values from ever being inserted.
--
-- CANONICAL ROLES (7):
--   SUPER_ADMIN, MEDICAL_REVIEWER, ACCOUNTANT, PROVIDER_STAFF,
--   EMPLOYER_ADMIN, DATA_ENTRY, FINANCE_VIEWER
-- ============================================================================

-- Step 0: Drop legacy user_type/user-assignment constraints from older migrations.
-- Must happen BEFORE normalization updates, otherwise updates may violate old checks.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_employer_or_provider;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_user_type_allowed;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_type_valid;

-- Step 1: Normalize any remaining legacy role values (safety net)
UPDATE users SET user_type = 'ACCOUNTANT' WHERE user_type = 'INSURANCE_ADMIN';
UPDATE users SET user_type = 'MEDICAL_REVIEWER' WHERE user_type = 'REVIEWER';
UPDATE users SET user_type = 'PROVIDER_STAFF' WHERE user_type IN ('PROVIDER', 'PROVIDER_USER', 'PROVIDER_ADMIN');
UPDATE users SET user_type = 'EMPLOYER_ADMIN' WHERE user_type IN ('EMPLOYER', 'BROKER');
UPDATE users SET user_type = 'SUPER_ADMIN' WHERE user_type = 'ADMIN';
UPDATE users SET user_type = 'DATA_ENTRY' WHERE user_type IS NULL OR user_type = '';

-- Step 1.1: Normalize foreign-key assignment shape to match canonical roles
UPDATE users SET employer_id = NULL WHERE user_type = 'PROVIDER_STAFF';
UPDATE users SET provider_id = NULL WHERE user_type = 'EMPLOYER_ADMIN';
UPDATE users
SET employer_id = NULL,
    provider_id = NULL
WHERE user_type IN ('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'DATA_ENTRY', 'FINANCE_VIEWER');

-- Step 2: Add CHECK constraint — exactly 7 valid roles
ALTER TABLE users ADD CONSTRAINT chk_user_type_valid
    CHECK (user_type IN (
        'SUPER_ADMIN',
        'MEDICAL_REVIEWER',
        'ACCOUNTANT',
        'PROVIDER_STAFF',
        'EMPLOYER_ADMIN',
        'DATA_ENTRY',
        'FINANCE_VIEWER'
    ));

-- Step 2.1: Enforce employer/provider assignment consistency for canonical roles
ALTER TABLE users ADD CONSTRAINT chk_user_employer_or_provider
    CHECK (
        (user_type = 'EMPLOYER_ADMIN' AND employer_id IS NOT NULL AND provider_id IS NULL)
        OR
        (user_type = 'PROVIDER_STAFF' AND provider_id IS NOT NULL AND employer_id IS NULL)
        OR
        (user_type IN ('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'ACCOUNTANT', 'DATA_ENTRY', 'FINANCE_VIEWER')
            AND employer_id IS NULL
            AND provider_id IS NULL)
    );

-- Step 3: Drop deprecated company_id column
ALTER TABLE users DROP COLUMN IF EXISTS company_id;

-- Step 4: Create index on user_type for authorization queries
CREATE INDEX IF NOT EXISTS idx_users_user_type ON users (user_type);

-- ============================================================================
-- RESULT: user_type is locked to exactly 7 values.
--         Any INSERT/UPDATE with an invalid role will be rejected by the DB.
-- ============================================================================
