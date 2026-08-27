-- ============================================================
-- V168: correct the idempotency key V167 established.
--
-- V167's index -- UNIQUE (employer_id, file_hash) WHERE status = 'COMPLETED'
-- -- had two real bugs, found by review after V167 had already shipped
-- (hence a new migration here, not an edit to V167 itself -- V167 may
-- already be applied to a development or test database, and Flyway
-- validates checksums of already-applied migrations):
--
--   1. Postgres treats NULL as distinct from NULL in a UNIQUE index, so the
--      identical file with employer_id left NULL (an "employer" column
--      inside the file itself, no single selected employer) could complete
--      arbitrarily many times without ever tripping the constraint.
--   2. The key ignored benefitPolicyId, the resolved header row, and
--      clearOldMembers -- all of which change what an import actually does.
--      Re-submitting the identical file with a different benefit policy
--      would have been recognized as "already done" and kept applying the
--      first run's policy.
--
-- import_scope_hash (application-computed: fileHash + employerId +
-- benefitPolicyId + resolvedHeaderRowNumber + clearOldMembers, see
-- MemberExcelImportService.executeImport) replaces the (employer_id,
-- file_hash) pair as the uniqueness key, sidestepping NULL-distinctness
-- entirely and covering every input that changes an import's outcome.
-- ============================================================

DROP INDEX IF EXISTS uk_member_import_logs_employer_filehash_completed;

ALTER TABLE member_import_logs
    ADD COLUMN IF NOT EXISTS import_scope_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_member_import_logs_scope_completed
    ON member_import_logs (import_scope_hash)
    WHERE status = 'COMPLETED' AND import_scope_hash IS NOT NULL;
