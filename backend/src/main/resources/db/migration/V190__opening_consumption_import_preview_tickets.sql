-- ============================================================
-- The same short-lived, single-use preview→execute contract V186 gave the
-- member Excel import, for the opening-consumption import (V188/V189):
-- execute may only run what preview already showed, on the same file.
-- ============================================================
CREATE TABLE opening_consumption_import_preview_tickets (
    token UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    reference_date DATE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_opening_import_preview_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_opening_import_preview_expiry ON opening_consumption_import_preview_tickets(expires_at)
    WHERE consumed_at IS NULL;
