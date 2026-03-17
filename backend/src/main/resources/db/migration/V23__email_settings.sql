-- ============================================================
-- V23: إعدادات البريد الإلكتروني (V220 + V224 مدمجان)
-- ============================================================

CREATE TABLE IF NOT EXISTS email_settings (
    id                  BIGSERIAL PRIMARY KEY,
    email_address       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(255),

    smtp_host           VARCHAR(255),
    smtp_port           INTEGER,
    smtp_username       VARCHAR(255),
    smtp_password       TEXT,

    imap_host           VARCHAR(255),
    imap_port           INTEGER,
    imap_username       VARCHAR(255),
    imap_password       TEXT,

    encryption_type     VARCHAR(20) DEFAULT 'TLS',
    listener_enabled    BOOLEAN DEFAULT FALSE,
    sync_interval_mins  INTEGER DEFAULT 5,
    last_sync_at        TIMESTAMP,

    -- فلترة البريد الوارد (V224)
    subject_filter      VARCHAR(255),
    only_from_providers BOOLEAN DEFAULT FALSE,

    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_email_settings_active ON email_settings(is_active) WHERE is_active = true;
