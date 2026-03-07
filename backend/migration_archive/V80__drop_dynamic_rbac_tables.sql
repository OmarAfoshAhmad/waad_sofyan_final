-- ============================================================================
-- V80: Drop Dynamic RBAC Tables — Authorization Simplification (Phase 5)
-- ============================================================================
-- WHAT: Remove dynamic permission system (roles, permissions, role_permissions,
--       user_roles tables) and rely on the static user_type column in users table.
--
-- WHY: Replacing dynamic RBAC with static role-based authorization.
--      All authorization now uses @PreAuthorize("hasRole('ROLE_NAME')") checks
--      where the role comes from users.user_type column.
--
-- SAFETY: Development environment only. No production data.
-- ============================================================================

-- Step 1: Ensure every user has a valid user_type from their current role assignment.
-- Map existing roles from user_roles join to user_type if user_type is NULL or empty.
UPDATE users u
SET user_type = COALESCE(
    (SELECT r.name FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = u.id LIMIT 1),
    u.user_type,
    'DATA_ENTRY'
)
WHERE u.user_type IS NULL OR u.user_type = '';

-- Step 2: Normalize legacy role names to new SystemRole enum values
UPDATE users SET user_type = 'SUPER_ADMIN' WHERE user_type IN ('SUPER_ADMIN', 'INSURANCE_ADMIN', 'ADMIN');
UPDATE users SET user_type = 'MEDICAL_REVIEWER' WHERE user_type IN ('REVIEWER', 'MEDICAL_REVIEWER');
UPDATE users SET user_type = 'PROVIDER_STAFF' WHERE user_type IN ('PROVIDER', 'PROVIDER_USER', 'PROVIDER_ADMIN');
UPDATE users SET user_type = 'EMPLOYER_ADMIN' WHERE user_type IN ('EMPLOYER', 'EMPLOYER_ADMIN', 'BROKER');
-- DATA_ENTRY, ACCOUNTANT, FINANCE_VIEWER are new — no existing mappings needed

-- Step 3: Set user_type NOT NULL constraint (it should already be NOT NULL, but enforce)
ALTER TABLE users ALTER COLUMN user_type SET NOT NULL;
ALTER TABLE users ALTER COLUMN user_type SET DEFAULT 'DATA_ENTRY';

-- Step 4: Drop join tables first (FK dependencies)
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;

-- Step 5: Drop entity tables
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS roles CASCADE;

-- Step 6: Drop sequences used by the RBAC tables
DROP SEQUENCE IF EXISTS permission_seq CASCADE;
DROP SEQUENCE IF EXISTS role_seq CASCADE;

-- ============================================================================
-- RESULT: users.user_type is now the sole source of role assignment.
-- Valid values: SUPER_ADMIN, MEDICAL_REVIEWER, ACCOUNTANT, PROVIDER_STAFF,
--              EMPLOYER_ADMIN, DATA_ENTRY, FINANCE_VIEWER
-- ============================================================================
