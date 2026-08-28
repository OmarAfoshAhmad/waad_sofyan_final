-- ============================================================
-- Safe rollback for bulk member imports.
--
-- member_import_logs already records that a batch happened and how many
-- rows it touched, but never WHICH members -- so there was no way to undo
-- one. This adds that link (member_import_batch_rows), the rollback
-- attempt itself as its own append-only batch (member_import_rollbacks,
-- same reason+actor+audit shape as every other batched operation in this
-- system), and the record of who got excluded and why
-- (member_import_rollback_skips).
-- ============================================================

CREATE TABLE member_import_batch_rows (
    id                BIGSERIAL PRIMARY KEY,
    import_log_id     BIGINT NOT NULL REFERENCES member_import_logs(id) ON DELETE RESTRICT,
    -- Deliberately no FK to members: CREATED members may be physically
    -- removed by rollback while this audit evidence must survive.
    member_id         BIGINT NOT NULL,

    action            VARCHAR(10) NOT NULL CHECK (action IN ('CREATED', 'UPDATED')),

    -- The member's own mutable fields as they stood BEFORE this row's
    -- import wrote them -- only the fields the importer can actually
    -- change, not the whole entity. NULL for a CREATED row (there is no
    -- "before"); required for an UPDATED row (there is nothing to revert
    -- to otherwise).
    previous_snapshot JSONB,
    -- Exact import-owned values immediately after the row was applied.
    -- Rollback compares this with the current row and refuses to overwrite
    -- any member that was edited after the import.
    imported_snapshot JSONB NOT NULL,

    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_import_batch_row_snapshot_shape
        CHECK ((action = 'CREATED') = (previous_snapshot IS NULL)),
    CONSTRAINT uq_import_batch_row UNIQUE (import_log_id, member_id)
);

CREATE INDEX idx_import_batch_rows_log ON member_import_batch_rows (import_log_id);
CREATE INDEX idx_import_batch_rows_member ON member_import_batch_rows (member_id);

CREATE TABLE member_import_rollbacks (
    id                       BIGSERIAL PRIMARY KEY,
    -- Audit attempts are written in REQUIRES_NEW while the business
    -- transaction may hold FOR UPDATE on member_import_logs. A FK check here
    -- would wait on that outer lock and self-deadlock. Keep the immutable
    -- logical identifier without a physical FK, as with other durable audit
    -- records that must survive/describe a failed transaction.
    import_log_id            BIGINT NOT NULL,

    reason                   VARCHAR(500) NOT NULL,
    performed_by             VARCHAR(120) NOT NULL,

    status                   VARCHAR(10) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    reverted_created_count   INTEGER NOT NULL DEFAULT 0,
    reverted_updated_count   INTEGER NOT NULL DEFAULT 0,
    skipped_count            INTEGER NOT NULL DEFAULT 0,
    error_message            TEXT,

    started_at               TIMESTAMP NOT NULL,
    completed_at              TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_import_rollback_reason_not_blank CHECK (btrim(reason) <> '')
);

-- A batch may be rolled back successfully at most once. This is the real
-- guard -- the service checks first for a clean error message, but this is
-- what actually prevents a double rollback under concurrent requests.
CREATE UNIQUE INDEX uq_import_rollback_completed_once
    ON member_import_rollbacks (import_log_id)
    WHERE status = 'COMPLETED';

CREATE TABLE member_import_rollback_skips (
    id            BIGSERIAL PRIMARY KEY,
    rollback_id   BIGINT NOT NULL REFERENCES member_import_rollbacks(id) ON DELETE CASCADE,
    member_id     BIGINT NOT NULL,
    reason        VARCHAR(50) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_import_rollback_skips_rollback ON member_import_rollback_skips (rollback_id);

CREATE OR REPLACE FUNCTION reject_member_import_rollback_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_member_import_batch_rows_append_only
    BEFORE UPDATE OR DELETE ON member_import_batch_rows
    FOR EACH ROW EXECUTE FUNCTION reject_member_import_rollback_audit_mutation();

CREATE TRIGGER trg_member_import_rollbacks_append_only
    BEFORE UPDATE OR DELETE ON member_import_rollbacks
    FOR EACH ROW EXECUTE FUNCTION reject_member_import_rollback_audit_mutation();

CREATE TRIGGER trg_member_import_rollback_skips_append_only
    BEFORE UPDATE OR DELETE ON member_import_rollback_skips
    FOR EACH ROW EXECUTE FUNCTION reject_member_import_rollback_audit_mutation();
