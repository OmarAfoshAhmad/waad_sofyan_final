-- ============================================================
-- V051: Member bulk import logs and error records
-- ============================================================
-- Depends on: V050 (members) — member_import_errors FKs to import_logs

-- ----------------------------------------------------------
-- SECTION 1: Member import batch log
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS member_import_logs (
    id                      BIGSERIAL PRIMARY KEY,
    import_batch_id         VARCHAR(64) NOT NULL UNIQUE,
    file_name               VARCHAR(500),
    file_size_bytes         BIGINT,

    -- Statistics
    total_rows      INTEGER DEFAULT 0,
    created_count   INTEGER DEFAULT 0,
    updated_count   INTEGER DEFAULT 0,
    skipped_count   INTEGER DEFAULT 0,
    error_count     INTEGER DEFAULT 0,

    -- Status
    status          VARCHAR(30) DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VALIDATING','PROCESSING','COMPLETED','PARTIAL','FAILED')),
    error_message   TEXT,

    -- Timestamps
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    processing_time_ms  BIGINT,

    -- Security context
    imported_by_user_id  BIGINT,
    imported_by_username VARCHAR(100),
    company_scope_id     BIGINT,
    ip_address           VARCHAR(45),
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_member_import_logs_batch   ON member_import_logs(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_member_import_logs_status  ON member_import_logs(status);
CREATE INDEX IF NOT EXISTS idx_member_import_logs_user    ON member_import_logs(imported_by_user_id)
    WHERE imported_by_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_member_import_logs_created ON member_import_logs(created_at DESC);

-- ----------------------------------------------------------
-- SECTION 2: Per-row import error records
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS member_import_errors (
    id              BIGSERIAL PRIMARY KEY,
    import_log_id   BIGINT NOT NULL,
    row_number      INTEGER NOT NULL,
    row_data        JSONB,
    error_type      VARCHAR(50),
    error_field     VARCHAR(100),
    error_message   TEXT,
    created_at      TIMESTAMP,

    CONSTRAINT fk_import_errors_log FOREIGN KEY (import_log_id)
        REFERENCES member_import_logs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_import_errors_log_id     ON member_import_errors(import_log_id);
CREATE INDEX IF NOT EXISTS idx_import_errors_row_number ON member_import_errors(row_number);
CREATE INDEX IF NOT EXISTS idx_import_errors_error_type ON member_import_errors(error_type);
