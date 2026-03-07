-- ============================================================
-- V015: System configuration tables
-- ============================================================
-- Depends on: nothing (standalone config),
--   module_access references feature_flags (same file, created first)

-- ----------------------------------------------------------
-- SECTION 1: System settings (key-value store)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_settings (
    id               BIGSERIAL PRIMARY KEY,
    setting_key      VARCHAR(100) NOT NULL UNIQUE,
    setting_value    TEXT,
    value_type       VARCHAR(20),
    description      VARCHAR(500),
    category         VARCHAR(50),
    is_editable      BOOLEAN DEFAULT true,
    default_value    TEXT,
    validation_rules TEXT,
    active           BOOLEAN DEFAULT true,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_system_settings_key    ON system_settings(setting_key);
CREATE INDEX IF NOT EXISTS idx_system_settings_active ON system_settings(active) WHERE active = true;

-- ----------------------------------------------------------
-- SECTION 2: Feature flags (runtime feature toggles)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS feature_flags (
    id          BIGSERIAL PRIMARY KEY,
    flag_key    VARCHAR(100) NOT NULL UNIQUE,
    flag_name   VARCHAR(255) NOT NULL,
    description TEXT,
    enabled     BOOLEAN DEFAULT true,
    role_filters JSON,
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

-- ----------------------------------------------------------
-- SECTION 3: Module access control (RBAC augmentation)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS module_access (
    id                  BIGSERIAL PRIMARY KEY,
    module_name         VARCHAR(100) NOT NULL,
    module_key          VARCHAR(100) NOT NULL,
    description         TEXT,
    allowed_roles       JSON NOT NULL,
    required_permissions JSON,
    feature_flag_key    VARCHAR(100),
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_module_access_module_key ON module_access(module_key);

-- ----------------------------------------------------------
-- SECTION 4: System-level audit log
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    timestamp   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id     BIGINT,
    username    VARCHAR(50),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    details     TEXT,
    ip_address  VARCHAR(45),
    user_agent  TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user      ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity    ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp DESC);

-- ----------------------------------------------------------
-- SECTION 5: PDF company branding settings
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdf_company_settings (
    id              BIGSERIAL PRIMARY KEY,
    company_name    VARCHAR(255) NOT NULL,
    logo_url        VARCHAR(512),
    logo_data       BYTEA,
    address         TEXT,
    phone           VARCHAR(50),
    email           VARCHAR(100),
    website         VARCHAR(255),
    footer_text     TEXT,
    footer_text_en  TEXT,
    header_color    VARCHAR(7),
    footer_color    VARCHAR(7),
    page_size       VARCHAR(20),
    margin_top      INTEGER,
    margin_bottom   INTEGER,
    margin_left     INTEGER,
    margin_right    INTEGER,
    is_active       BOOLEAN,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);
