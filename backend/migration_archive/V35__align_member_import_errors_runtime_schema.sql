-- V35: Create member_import_errors table required by MemberImportError entity

CREATE TABLE IF NOT EXISTS member_import_errors (
    id BIGSERIAL PRIMARY KEY,
    import_log_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,
    row_data JSONB,
    error_type VARCHAR(50),
    error_field VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP,
    CONSTRAINT fk_member_import_errors_log
        FOREIGN KEY (import_log_id) REFERENCES member_import_logs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_member_import_errors_log_id ON member_import_errors(import_log_id);
CREATE INDEX IF NOT EXISTS idx_member_import_errors_row_number ON member_import_errors(row_number);
CREATE INDEX IF NOT EXISTS idx_member_import_errors_error_type ON member_import_errors(error_type);
