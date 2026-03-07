-- ============================================================
-- V010: Users (authentication + profile)
-- ============================================================
-- Depends on: V005 (employers), V006 (providers)

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT PRIMARY KEY DEFAULT nextval('user_seq'),
    username    VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,

    -- User type (flattened RBAC — replaces dynamic roles/permissions)
    user_type VARCHAR(50) NOT NULL DEFAULT 'DATA_ENTRY'
        CHECK (user_type IN (
            'SUPER_ADMIN','EMPLOYER_ADMIN','MEDICAL_REVIEWER',
            'PROVIDER_STAFF','ACCOUNTANT','FINANCE_VIEWER','DATA_ENTRY'
        )),

    -- Organizational relationships
    employer_id BIGINT,
    provider_id BIGINT,
    company_id  BIGINT,

    -- Account status flags
    enabled                     BOOLEAN DEFAULT true,
    account_non_expired         BOOLEAN DEFAULT true,
    account_non_locked          BOOLEAN DEFAULT true,
    credentials_non_expired     BOOLEAN DEFAULT true,
    is_active                   BOOLEAN DEFAULT true,
    email_verified              BOOLEAN DEFAULT false,

    -- Module-level permission overrides (legacy columns — kept for compatibility)
    can_view_claims             BOOLEAN DEFAULT true,
    can_view_visits             BOOLEAN DEFAULT true,
    can_view_reports            BOOLEAN DEFAULT true,
    can_view_members            BOOLEAN DEFAULT true,
    can_view_benefit_policies   BOOLEAN DEFAULT true,

    -- Identity verification
    identity_verified       BOOLEAN DEFAULT false,
    identity_verified_at    TIMESTAMP,
    identity_verified_by    VARCHAR(255),

    -- Extended profile
    phone               VARCHAR(50),
    profile_image_url   VARCHAR(500),

    -- Security tracking
    password_changed_at     TIMESTAMP,
    failed_login_count      INTEGER DEFAULT 0,
    locked_until            TIMESTAMP,
    last_login_at           TIMESTAMP,

    -- Audit
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login  TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT fk_user_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email    ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_type     ON users(user_type);
CREATE INDEX IF NOT EXISTS idx_users_enabled  ON users(enabled);
CREATE INDEX IF NOT EXISTS idx_users_active   ON users(is_active);
CREATE INDEX IF NOT EXISTS idx_users_employer ON users(employer_id) WHERE employer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider_id) WHERE provider_id IS NOT NULL;
