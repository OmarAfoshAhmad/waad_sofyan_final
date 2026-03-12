-- ============================================================
-- Table: system_settings
-- Depends on: (none)
-- ============================================================
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
