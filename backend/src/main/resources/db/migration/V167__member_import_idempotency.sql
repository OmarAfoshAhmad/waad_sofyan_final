-- ============================================================
-- V167: idempotency for member Excel import (MemberExcelImportService)
--
-- member_import_logs already had a unique import_batch_id, but that ID is
-- minted fresh by the client on every /preview call -- it does not by
-- itself prevent the SAME logical import (same file content AND the same
-- employer/policy/header-row/clear-old-members choices) from being fully
-- re-run twice.
--
-- import_scope_hash is a single fingerprint of every input that changes the
-- OUTCOME of an import: the file's own bytes (file_hash) plus employerId,
-- benefitPolicyId, the resolved header row number, and clearOldMembers.
-- Keying uniqueness on file_hash + employer_id alone (an earlier version of
-- this migration) was wrong twice over:
--   1. Postgres treats NULL as distinct from NULL in a UNIQUE index, so the
--      same file with employer_id left NULL (an employer column inside the
--      file itself, no single selected employer) could be "completed"
--      arbitrarily many times without ever tripping the constraint.
--   2. Re-running the identical file with a DIFFERENT benefit policy, a
--      different header row, or clearOldMembers flipped is a genuinely
--      different operation with a different intended outcome -- collapsing
--      it into "the same import" would silently apply the FIRST run's
--      choices and skip the second run's real intent (e.g. a corrective
--      re-import with clearOldMembers=true would be treated as already done
--      and never actually clear anything).
--
-- Only COMPLETED imports are covered -- a PROCESSING/FAILED row must never
-- block a legitimate retry of the same logical import.
-- ============================================================

ALTER TABLE member_import_logs
    ADD COLUMN IF NOT EXISTS file_hash         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS employer_id       BIGINT,
    ADD COLUMN IF NOT EXISTS import_scope_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_member_import_logs_scope_completed
    ON member_import_logs (import_scope_hash)
    WHERE status = 'COMPLETED' AND import_scope_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_member_import_logs_employer ON member_import_logs(employer_id);
