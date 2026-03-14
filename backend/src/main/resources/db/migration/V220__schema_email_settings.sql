-- ============================================================
-- V220: Email Configuration for Pre-Authorization System
-- ============================================================

CREATE TABLE IF NOT EXISTS email_settings (
    id                  BIGSERIAL PRIMARY KEY,
    email_address       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(255),
    
    -- SMTP Configuration
    smtp_host           VARCHAR(255),
    smtp_port           INTEGER,
    smtp_username       VARCHAR(255),
    smtp_password       TEXT, -- Encrypted
    
    -- IMAP Configuration
    imap_host           VARCHAR(255),
    imap_port           INTEGER,
    imap_username       VARCHAR(255),
    imap_password       TEXT, -- Encrypted
    
    -- Sync Settings
    encryption_type     VARCHAR(20) DEFAULT 'TLS', -- SSL, TLS, NONE
    listener_enabled    BOOLEAN DEFAULT FALSE,
    sync_interval_mins  INTEGER DEFAULT 5,
    last_sync_at        TIMESTAMP,
    
    -- Common Fields
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

-- Index for active settings
CREATE INDEX idx_email_settings_active ON email_settings(is_active) WHERE is_active = true;

-- Sample entry (disabled by default)
INSERT INTO email_settings (email_address, display_name, smtp_host, smtp_port, encryption_type, listener_enabled)
VALUES ('preauth@alwahacare.com', 'Alwahacare Pre-Auth', 'smtp.hostinger.com', 587, 'TLS', FALSE);
