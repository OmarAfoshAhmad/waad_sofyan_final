-- Maintenance Center — Phase 1: system backup (manual + scheduled) and retention.
--
-- Consolidates what the reference implementation split across two historical
-- migrations (table creation, then ALTER for the scheduler/retention columns)
-- into one coherent schema, since this is a fresh install here.

CREATE TABLE IF NOT EXISTS system_backup_settings (
    id                        BIGINT       PRIMARY KEY,
    local_enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    local_display_name        VARCHAR(150) NOT NULL DEFAULT 'المسار المحلي الأساسي',
    -- Server/Docker-configured only; never accepts a browser-submitted path.
    local_path                VARCHAR(1000) NOT NULL,
    retention_days            INTEGER      NOT NULL DEFAULT 30,

    -- Scheduled backup configuration
    auto_backup_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_backup_type          VARCHAR(40)  NOT NULL DEFAULT 'FULL_SYSTEM',
    auto_backup_hour          INTEGER      NOT NULL DEFAULT 2,
    auto_backup_minute        INTEGER      NOT NULL DEFAULT 0,
    last_auto_backup_at       TIMESTAMP,
    last_auto_backup_status   VARCHAR(30),
    last_auto_backup_message  VARCHAR(500),

    -- Retention purge tracking
    last_purge_at             TIMESTAMP,
    last_purge_status         VARCHAR(30),
    last_purge_message        VARCHAR(500),

    updated_by                VARCHAR(120),
    updated_at                TIMESTAMP,

    CONSTRAINT chk_backup_retention_days   CHECK (retention_days >= 1),
    CONSTRAINT chk_backup_auto_hour        CHECK (auto_backup_hour BETWEEN 0 AND 23),
    CONSTRAINT chk_backup_auto_minute      CHECK (auto_backup_minute BETWEEN 0 AND 59),
    CONSTRAINT chk_backup_auto_type        CHECK (auto_backup_type IN ('DATABASE_ONLY', 'FILES_ONLY', 'FULL_SYSTEM'))
);

CREATE TABLE IF NOT EXISTS system_backup_jobs (
    id                 BIGSERIAL     PRIMARY KEY,
    type               VARCHAR(40)   NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    file_name          VARCHAR(255),
    file_path          VARCHAR(1000),
    file_size          BIGINT,
    checksum           VARCHAR(128),
    manifest_path      VARCHAR(1000),
    note               TEXT,
    created_by         VARCHAR(120),
    started_at         TIMESTAMP     NOT NULL,
    completed_at       TIMESTAMP,
    duration_ms        BIGINT,
    error_message      TEXT,
    environment        VARCHAR(40),
    git_commit         VARCHAR(80),
    encrypted          BOOLEAN       NOT NULL DEFAULT FALSE,
    destination_path   VARCHAR(1000),
    backup_format      VARCHAR(30),
    warnings           TEXT,

    CONSTRAINT chk_backup_job_type   CHECK (type IN ('DATABASE_ONLY', 'FILES_ONLY', 'FULL_SYSTEM')),
    CONSTRAINT chk_backup_job_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_system_backup_jobs_started_at ON system_backup_jobs (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_backup_jobs_status     ON system_backup_jobs (status);
