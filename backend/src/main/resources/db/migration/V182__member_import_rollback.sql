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
    member_id         BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,

    action            VARCHAR(10) NOT NULL CHECK (action IN ('CREATED', 'UPDATED')),

    -- The member's own mutable fields as they stood BEFORE this row's
    -- import wrote them -- only the fields the importer can actually
    -- change, not the whole entity. NULL for a CREATED row (there is no
    -- "before"); required for an UPDATED row (there is nothing to revert
    -- to otherwise).
    previous_snapshot JSONB,

    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_import_batch_row_snapshot_shape
        CHECK ((action = 'CREATED') = (previous_snapshot IS NULL)),
    CONSTRAINT uq_import_batch_row UNIQUE (import_log_id, member_id)
);

CREATE INDEX idx_import_batch_rows_log ON member_import_batch_rows (import_log_id);
CREATE INDEX idx_import_batch_rows_member ON member_import_batch_rows (member_id);

CREATE TABLE member_import_rollbacks (
    id                       BIGSERIAL PRIMARY KEY,
    import_log_id            BIGINT NOT NULL REFERENCES member_import_logs(id) ON DELETE RESTRICT,

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
