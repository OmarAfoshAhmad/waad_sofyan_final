-- ============================================================================
-- V1: Core Schema - Foundation Tables
-- ============================================================================
-- CLEAN MIGRATION REBASELINE - Development Environment
-- Domain Model: Employer is the ONLY top-level business entity
-- 
-- Creates: 
--   - Core sequences
--   - Employers table (companies)
--   - Providers table  
--   - Provider allowed employers
--   - Provider admin documents
--   - Users table
--   - Email/password reset tokens
--   - User login attempts
--   - User audit log
--   - System settings
-- 
-- NO organizations table - Employer-only architecture from day 1
-- ============================================================================

-- ============================================================================
-- SECTION 1: Core Sequences
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS employer_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS provider_seq START WITH 1 INCREMENT BY 50;

-- ============================================================================
-- SECTION 2: Employers (Top-level Business Entity)
-- ============================================================================

CREATE TABLE IF NOT EXISTS employers (
    id BIGINT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    
    -- Contact information
    address TEXT,
    phone VARCHAR(50),
    email VARCHAR(255),
    
    -- Branding & identity
    logo_url VARCHAR(500),
    website VARCHAR(200),
    business_type VARCHAR(100),
    tax_number VARCHAR(50),
    commercial_registration_number VARCHAR(50),
    
    -- Status
    active BOOLEAN NOT NULL DEFAULT true,
    is_default BOOLEAN NOT NULL DEFAULT false,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_employers_code ON employers(code);
CREATE INDEX IF NOT EXISTS idx_employers_active ON employers(active);
CREATE INDEX IF NOT EXISTS idx_employers_default ON employers(is_default) WHERE is_default = true;

COMMENT ON TABLE employers IS 'Employer companies - the ONLY top-level business entity in the system';

-- ============================================================================
-- SECTION 3: Providers (Healthcare Facilities)
-- ============================================================================

CREATE TABLE IF NOT EXISTS providers (
    id BIGINT PRIMARY KEY,
    provider_name VARCHAR(255) NOT NULL,
    provider_name_ar VARCHAR(255),
    license_number VARCHAR(100) UNIQUE NOT NULL,
    provider_type VARCHAR(50) NOT NULL CHECK (provider_type IN ('HOSPITAL', 'CLINIC', 'PHARMACY', 'LAB', 'RADIOLOGY', 'OTHER')),
    
    -- Contact information
    contact_person VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    region VARCHAR(100),
    
    -- Banking information (for settlement)
    bank_name VARCHAR(255),
    bank_account_number VARCHAR(100),
    iban VARCHAR(50),
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_providers_type ON providers(provider_type) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_providers_active ON providers(active);
CREATE INDEX IF NOT EXISTS idx_providers_license ON providers(license_number);

COMMENT ON TABLE providers IS 'Healthcare facilities providing medical services';

-- ============================================================================
-- SECTION 4: Provider Allowed Employers (Access Control)
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_allowed_employers (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    employer_id BIGINT NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_allowed_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT fk_allowed_employer FOREIGN KEY (employer_id)
        REFERENCES employers(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_employer UNIQUE (provider_id, employer_id)
);

CREATE INDEX IF NOT EXISTS idx_allowed_employers_provider ON provider_allowed_employers(provider_id);
CREATE INDEX IF NOT EXISTS idx_allowed_employers_employer ON provider_allowed_employers(employer_id);

COMMENT ON TABLE provider_allowed_employers IS 'Defines which employers can use which providers';

-- ============================================================================
-- SECTION 5: Provider Admin Documents
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_admin_documents (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    
    -- Audit fields
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255),
    
    CONSTRAINT fk_provider_docs FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_provider_docs_provider ON provider_admin_documents(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_docs_type ON provider_admin_documents(document_type);

COMMENT ON TABLE provider_admin_documents IS 'Administrative documents (licenses, contracts, etc)';

-- ============================================================================
-- SECTION 6: Users Table (Foundation for Authentication)
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    
    -- User type and relationships
    user_type VARCHAR(50) NOT NULL CHECK (user_type IN ('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_USER')),
    employer_id BIGINT,
    provider_id BIGINT,
    
    -- Account status
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_non_expired BOOLEAN NOT NULL DEFAULT true,
    account_non_locked BOOLEAN NOT NULL DEFAULT true,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT true,
    
    -- Custom permissions (feature flags)
    can_view_claims BOOLEAN NOT NULL DEFAULT true,
    can_view_visits BOOLEAN NOT NULL DEFAULT true,
    can_view_reports BOOLEAN NOT NULL DEFAULT true,
    can_view_members BOOLEAN NOT NULL DEFAULT true,
    can_view_benefit_policies BOOLEAN NOT NULL DEFAULT true,
    
    -- Identity verification
    identity_verified BOOLEAN NOT NULL DEFAULT false,
    identity_verified_at TIMESTAMP,
    identity_verified_by VARCHAR(255),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_user_employer FOREIGN KEY (employer_id) 
        REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_employer_or_provider CHECK (
        (user_type = 'SUPER_ADMIN' AND employer_id IS NULL AND provider_id IS NULL) OR
        (user_type = 'EMPLOYER_ADMIN' AND employer_id IS NOT NULL AND provider_id IS NULL) OR
        (user_type = 'PROVIDER_USER' AND provider_id IS NOT NULL AND employer_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_type ON users(user_type);
CREATE INDEX IF NOT EXISTS idx_users_employer ON users(employer_id) WHERE employer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider_id) WHERE provider_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users(enabled);

COMMENT ON TABLE users IS 'System users with type-based access control';
COMMENT ON COLUMN users.user_type IS 'SUPER_ADMIN, EMPLOYER_ADMIN, or PROVIDER_USER';
COMMENT ON CONSTRAINT chk_user_employer_or_provider ON users IS 'Ensures correct employer/provider assignment per user type';

-- ============================================================================
-- SECTION 7: Email Verification Tokens
-- ============================================================================

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT false,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_email_verify_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_email_tokens_user ON email_verification_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_email_tokens_token ON email_verification_tokens(token);
CREATE INDEX IF NOT EXISTS idx_email_tokens_expiry ON email_verification_tokens(expiry_date);

COMMENT ON TABLE email_verification_tokens IS 'Tokens for email verification flow';

-- ============================================================================
-- SECTION 8: Password Reset Tokens
-- ============================================================================

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_tokens_token ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_tokens_expiry ON password_reset_tokens(expiry_date);

COMMENT ON TABLE password_reset_tokens IS 'Tokens for password reset flow';

-- ============================================================================
-- SECTION 9: User Login Attempts (Security)
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_login_attempts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50),
    user_agent TEXT,
    attempt_result VARCHAR(20) NOT NULL CHECK (attempt_result IN ('SUCCESS', 'FAILURE', 'LOCKED')),
    failure_reason VARCHAR(500),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username ON user_login_attempts(username);
CREATE INDEX IF NOT EXISTS idx_login_attempts_created ON user_login_attempts(created_at);
CREATE INDEX IF NOT EXISTS idx_login_attempts_result ON user_login_attempts(attempt_result);

COMMENT ON TABLE user_login_attempts IS 'Audit trail for login attempts - security monitoring';

-- ============================================================================
-- SECTION 10: User Audit Log
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(255) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    action_description TEXT,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(50),
    user_agent TEXT,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_username ON user_audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_action_type ON user_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON user_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON user_audit_log(created_at);

COMMENT ON TABLE user_audit_log IS 'Comprehensive audit trail for all user actions';

-- ============================================================================
-- SECTION 11: System Settings
-- ============================================================================

CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    value_type VARCHAR(20) NOT NULL, -- Enum handled by app validation or check constraint
    description VARCHAR(500),
    category VARCHAR(50),
    is_editable BOOLEAN NOT NULL DEFAULT true,
    default_value TEXT,
    validation_rules TEXT,
    active BOOLEAN NOT NULL DEFAULT true,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_system_settings_key ON system_settings(setting_key);
CREATE INDEX IF NOT EXISTS idx_system_settings_active ON system_settings(active) WHERE active = true;

COMMENT ON TABLE system_settings IS 'Global system configuration key-value store';

-- ============================================================================
-- Migration Complete: V1
-- ============================================================================
-- Created: Employers, Providers, Users, Security tokens, Audit logs
-- Architecture: Employer-only model (no organizations table)
-- Ready for: RBAC setup (V2) and business entities (V4)
-- ============================================================================
