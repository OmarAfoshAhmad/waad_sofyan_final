-- ============================================================
-- Table: member_import_logs
-- Depends on: (none)
-- ============================================================
CREATE TABLE IF NOT EXISTS member_import_logs (
    id                    BIGSERIAL PRIMARY KEY,
    import_batch_id       VARCHAR(64) NOT NULL UNIQUE,
    file_name             VARCHAR(500),
    file_size_bytes       BIGINT,

    total_rows            INTEGER DEFAULT 0,
    created_count         INTEGER DEFAULT 0,
    updated_count         INTEGER DEFAULT 0,
    skipped_count         INTEGER DEFAULT 0,
    error_count           INTEGER DEFAULT 0,

    status                VARCHAR(30) DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VALIDATING','PROCESSING','COMPLETED','PARTIAL','FAILED')),
    error_message         TEXT,

    started_at            TIMESTAMP,
    completed_at          TIMESTAMP,
    processing_time_ms    BIGINT,

    imported_by_user_id   BIGINT,
    imported_by_username  VARCHAR(100),
    company_scope_id      BIGINT,
    ip_address            VARCHAR(45),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_member_import_logs_batch   ON member_import_logs(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_member_import_logs_status  ON member_import_logs(status);
CREATE INDEX IF NOT EXISTS idx_member_import_logs_user    ON member_import_logs(imported_by_user_id)
    WHERE imported_by_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_member_import_logs_created ON member_import_logs(created_at DESC);
