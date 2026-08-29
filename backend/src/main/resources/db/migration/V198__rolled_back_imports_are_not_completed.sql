-- An import that was reverted stops counting as completed.
--
-- The rollback wrote its own record and left member_import_logs at COMPLETED,
-- so a batch whose rows had been deleted still read as intact on the history
-- screen -- and, worse, the idempotency guard still recognised its fingerprint
-- and refused to import the same file again. Rollback was a one-way door:
-- revert once and that file could never be loaded again.
--
-- Backfills every log that already has a completed rollback against it. The
-- rollback rows are the record of what happened; this only makes the log agree
-- with them.
--
-- Logs with skipped rows are deliberately left alone: part of that import
-- survived the revert, and calling it fully reverted would be a worse
-- inaccuracy than the one being corrected.
-- The column has a CHECK listing the statuses it accepts, so the new one has
-- to be admitted before any row can carry it. The constraint is what caught
-- the omission -- an enum widened in Java and not in the schema would
-- otherwise have failed at the first rollback in production.
ALTER TABLE member_import_logs DROP CONSTRAINT IF EXISTS member_import_logs_status_check;
ALTER TABLE member_import_logs ADD CONSTRAINT member_import_logs_status_check
    CHECK (status IN ('PENDING', 'VALIDATING', 'PROCESSING', 'COMPLETED',
                      'PARTIAL', 'FAILED', 'ROLLED_BACK'));

UPDATE member_import_logs l
SET status = 'ROLLED_BACK'
WHERE l.status = 'COMPLETED'
  AND EXISTS (
      SELECT 1 FROM member_import_rollbacks r
      WHERE r.import_log_id = l.id
        AND r.status = 'COMPLETED'
        AND COALESCE(r.skipped_count, 0) = 0
  );
