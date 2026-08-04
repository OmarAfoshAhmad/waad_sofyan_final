-- Maintenance Center — Phase 2: monitoring, Telegram alerts, health checks.
--
-- The reference implementation split this across three migrations (settings,
-- alert-rule columns, external heartbeat columns). Consolidated here since this
-- is a fresh install. telegram_bot_token stores the ciphertext produced by
-- SecretEncryptionService ("enc:v1:..."), never plaintext — see
-- MonitoringSettingsService.

CREATE TABLE IF NOT EXISTS system_monitoring_settings (
    id                                BIGINT       PRIMARY KEY,
    telegram_enabled                  BOOLEAN      NOT NULL DEFAULT FALSE,
    telegram_bot_token                VARCHAR(500),
    telegram_chat_id                  VARCHAR(120),
    telegram_thread_id                VARCHAR(120),
    alert_environment                 VARCHAR(80)  NOT NULL DEFAULT 'local',
    min_interval_seconds              INTEGER      NOT NULL DEFAULT 300,
    recovery_enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by                        VARCHAR(120),
    updated_at                        TIMESTAMP,
    last_test_at                      TIMESTAMP,
    last_test_status                  VARCHAR(30),
    last_test_message                 VARCHAR(500),

    automatic_monitoring_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    check_interval_seconds            INTEGER      NOT NULL DEFAULT 300,
    disk_warning_percent              INTEGER      NOT NULL DEFAULT 80,
    disk_critical_percent             INTEGER      NOT NULL DEFAULT 90,
    max_backup_age_hours              INTEGER      NOT NULL DEFAULT 72,
    repeated_error_threshold          INTEGER      NOT NULL DEFAULT 10,
    repeated_error_window_minutes     INTEGER      NOT NULL DEFAULT 15,
    alert_cooldown_seconds            INTEGER      NOT NULL DEFAULT 1800,
    last_auto_check_at                TIMESTAMP,
    last_auto_check_status            VARCHAR(30),
    last_auto_check_message           VARCHAR(500),

    last_external_heartbeat_at        TIMESTAMP,
    last_external_heartbeat_source    VARCHAR(120),
    last_external_heartbeat_status    VARCHAR(30),

    CONSTRAINT chk_monitoring_disk_percents
        CHECK (disk_warning_percent BETWEEN 1 AND 99 AND disk_critical_percent BETWEEN 2 AND 100)
);

CREATE TABLE IF NOT EXISTS system_monitoring_alert_state (
    alert_key         VARCHAR(80)  PRIMARY KEY,
    status            VARCHAR(30)  NOT NULL DEFAULT 'HEALTHY',
    severity          INTEGER      NOT NULL DEFAULT 0,
    first_detected_at TIMESTAMP,
    last_detected_at  TIMESTAMP,
    last_sent_at      TIMESTAMP,
    recovered_at      TIMESTAMP,
    last_summary      VARCHAR(1000),
    alert_count       INTEGER      NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP    NOT NULL,

    CONSTRAINT chk_monitoring_alert_status CHECK (status IN ('HEALTHY', 'WARNING', 'CRITICAL', 'UNKNOWN'))
);

-- Rolling window of 5xx responses; ErrorRateMonitor purges rows older than 7 days.
CREATE TABLE IF NOT EXISTS system_monitoring_error_events (
    id           BIGSERIAL    PRIMARY KEY,
    occurred_at  TIMESTAMP    NOT NULL,
    status_code  INTEGER      NOT NULL,
    method       VARCHAR(20),
    path         VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_monitoring_alert_state_updated_at ON system_monitoring_alert_state (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitoring_error_events_occurred_at ON system_monitoring_error_events (occurred_at DESC);
