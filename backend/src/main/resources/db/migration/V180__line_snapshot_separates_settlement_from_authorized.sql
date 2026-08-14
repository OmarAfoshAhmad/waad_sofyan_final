-- ============================================================
-- V180: the line snapshot inherits the distinction V176 drew at head level.
--
-- V176 separated "what was authorised" from "what the two parties settle
-- between them", because the constitution's S13 invariant distributes the
-- ENTIRE requested amount across the parties -- so a total built from the
-- shares equals the request even when part of the service was refused.
--
-- That fix stopped at the head. The LINE snapshot kept one approved_amount
-- and required patient_share + company_share to equal it, which holds only
-- while nothing is refused. The moment a reviewer cuts a quantity, the two
-- numbers legitimately differ and the row cannot be written at all: the
-- authorised value drops, while the shares still account for the whole
-- request.
--
-- Same split, same names, so head and line read alike.
-- ============================================================

ALTER TABLE preauth_line_snapshots
    -- What the two parties pay between them for this line.
    ADD COLUMN IF NOT EXISTS settlement_amount NUMERIC(15,2);

-- Existing rows were written before any refusal could be expressed, so the
-- two figures were necessarily equal for them.
--
-- The snapshot is append-only, and its trigger refuses every UPDATE -- which
-- is the point of it. A new column added today has no value on rows written
-- yesterday, and only an UPDATE can give it one, so the guard is suspended
-- for this one statement and restored immediately. The statement sets a
-- column that did not exist a moment ago from one that did; no share, no
-- total and no reservation is touched, so no figure can move.
ALTER TABLE preauth_line_snapshots DISABLE TRIGGER trg_preauth_line_snapshot_no_update;

UPDATE preauth_line_snapshots SET settlement_amount = approved_amount
WHERE settlement_amount IS NULL;

ALTER TABLE preauth_line_snapshots ENABLE TRIGGER trg_preauth_line_snapshot_no_update;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_preauth_line_snapshot_no_update'
          AND tgrelid = 'preauth_line_snapshots'::regclass
          AND tgenabled <> 'D'
    ) THEN
        RAISE EXCEPTION
            'V180 aborted: the append-only UPDATE guard was not restored. '
            'A snapshot that can be edited is not a snapshot.';
    END IF;
END $$;

ALTER TABLE preauth_line_snapshots
    ALTER COLUMN settlement_amount SET NOT NULL;

ALTER TABLE preauth_line_snapshots
    DROP CONSTRAINT IF EXISTS chk_preauth_line_snapshot_shares;

ALTER TABLE preauth_line_snapshots
    -- The shares account for the SETTLEMENT, which is what they divide.
    ADD CONSTRAINT chk_preauth_line_snapshot_settlement CHECK (
        patient_share + company_share = settlement_amount),

    -- approved_amount now means the AUTHORISED service value: what was asked,
    -- less what was explicitly refused. A ceiling never reduces it -- a
    -- ceiling decides who pays, not what was authorised.
    ADD CONSTRAINT chk_preauth_line_snapshot_authorized CHECK (
        approved_amount + rejected_amount <= requested_amount),

    -- Neither figure may exceed the request.
    ADD CONSTRAINT chk_preauth_line_snapshot_settlement_bound CHECK (
        settlement_amount <= requested_amount);

COMMENT ON COLUMN preauth_line_snapshots.approved_amount IS
    'The AUTHORISED service value: requested less explicitly refused. Not '
    'reduced by a benefit ceiling, which decides who pays rather than what '
    'was authorised.';

COMMENT ON COLUMN preauth_line_snapshots.settlement_amount IS
    'What the two parties pay between them: patient_share + company_share.';
