-- ============================================================================
-- Align users.user_type constraints with modern RBAC roles
-- Root cause: legacy checks only supported SUPER_ADMIN / EMPLOYER_ADMIN / PROVIDER_USER
-- ============================================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_user_type_allowed;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_user_employer_or_provider;

ALTER TABLE users
    ADD CONSTRAINT chk_users_user_type_allowed CHECK (
        user_type IN (
            'SUPER_ADMIN',
            'EMPLOYER_ADMIN',
            'PROVIDER_USER',
            'PROVIDER_ADMIN',
            'INSURANCE_ADMIN',
            'REVIEWER',
            'MEDICAL_REVIEWER',
            'SYSTEM_USER'
        )
    );

ALTER TABLE users
    ADD CONSTRAINT chk_user_employer_or_provider CHECK (
        (user_type = 'EMPLOYER_ADMIN' AND employer_id IS NOT NULL AND provider_id IS NULL)
        OR
        (user_type IN ('PROVIDER_USER', 'PROVIDER_ADMIN') AND provider_id IS NOT NULL AND employer_id IS NULL)
        OR
        (user_type IN ('SUPER_ADMIN', 'INSURANCE_ADMIN', 'REVIEWER', 'MEDICAL_REVIEWER', 'SYSTEM_USER')
            AND employer_id IS NULL
            AND provider_id IS NULL)
    );
