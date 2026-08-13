-- ============================================================
-- V167: idempotency for member Excel import (MemberExcelImportService)
--
-- member_import_logs already had a unique import_batch_id, but that ID is
-- minted fresh by the client on every /preview call -- it does not by
-- itself prevent the SAME file content from being fully re-imported twice
-- for the same employer. Add a content fingerprint and enforce uniqueness
-- only across COMPLETED imports (a PROCESSING/FAILED row must never block a
-- legitimate retry of the same file).
-- ============================================================

ALTER TABLE member_import_logs
    ADD COLUMN IF NOT EXISTS file_hash  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS employer_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_member_import_logs_employer_filehash_completed
    ON member_import_logs (employer_id, file_hash)
    WHERE status = 'COMPLETED' AND file_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_member_import_logs_employer ON member_import_logs(employer_id);
