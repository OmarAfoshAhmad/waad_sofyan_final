-- ============================================================
-- Opening consumption: what a member had already spent before this system
-- started keeping their ledger.
--
-- V173 added source_type precisely so this would stop masquerading as a
-- claim -- an imported balance was previously written as a fabricated claim,
-- which put rows in the claims table for services nobody rendered and made
-- every claim-scoped report lie. OPENING_IMPORT has existed as a legal value
-- since then with nothing able to write it, because a movement of that kind
-- needs to say WHERE it came from, and there was nowhere to put that.
--
-- This migration gives it somewhere: a batch. One import is one batch row
-- carrying who performed it, why, and what external record it came from; and
-- every movement points at the batch that produced it. Without that link an
-- opening balance is an unattributable number sitting against a member's
-- limit, and the only remedy anyone would have is to edit it -- which the
-- append-only ledger correctly forbids.
-- ============================================================

CREATE TABLE member_opening_balance_batches (
    id                BIGSERIAL PRIMARY KEY,

    -- The caller's own identifier for this import. UNIQUE is the idempotency
    -- boundary: replaying a batch that already landed is rejected here rather
    -- than doubling a member's consumed balance.
    batch_reference   VARCHAR(120) NOT NULL UNIQUE,

    -- Why these balances exist at all. Not optional: an opening balance is an
    -- assertion about spending this system never saw, and "because it was in
    -- the file" is the answer that has to be written down somewhere.
    reason            VARCHAR(500) NOT NULL,

    -- Who is accountable. A row nobody performed cannot be questioned later.
    performed_by      VARCHAR(120) NOT NULL,

    -- Where the numbers came from -- prior system, spreadsheet, letter. The
    -- audit question is never "what is the balance" but "on what authority".
    source_reference  VARCHAR(500) NOT NULL,

    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_opening_batch_reference_not_blank CHECK (btrim(batch_reference) <> ''),
    CONSTRAINT chk_opening_batch_reason_not_blank    CHECK (btrim(reason) <> ''),
    CONSTRAINT chk_opening_batch_performer_not_blank CHECK (btrim(performed_by) <> ''),
    CONSTRAINT chk_opening_batch_source_not_blank    CHECK (btrim(source_reference) <> '')
);

-- A correction is a fourth reason a movement can be compensated. Adding it to
-- the Java enum alone would leave every correction failing its CHECK at the
-- moment it posted -- the exact shape of the defect V187 shipped with and had
-- to be caught by a test that asserted a correct write SUCCEEDS.
ALTER TABLE benefit_bucket_consumptions
    DROP CONSTRAINT chk_bucket_consumption_reversal_reason;

ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_bucket_consumption_reversal_reason
        CHECK (reversal_reason IS NULL OR reversal_reason IN
            ('PREAUTH_RELEASE', 'PREAUTH_EXPIRY', 'PREAUTH_CANCELLATION',
             'PREAUTH_CONVERSION_RELEASE', 'CLAIM_REVERSAL', 'CLAIM_CORRECTION',
             'OPENING_CORRECTION'));

ALTER TABLE benefit_bucket_consumptions
    ADD COLUMN opening_batch_id BIGINT
        REFERENCES member_opening_balance_batches(id) ON DELETE RESTRICT;

-- Exactly when, in both directions. An OPENING_IMPORT row without a batch is
-- the unattributable number this table exists to prevent; a batch id on a
-- claim or a hold would attribute a movement to an import that did not make
-- it. No backfill is needed or possible: nothing has ever written an
-- OPENING_IMPORT row, so the constraint is added already valid.
ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_opening_consumption_has_batch
        CHECK ((source_type = 'OPENING_IMPORT') = (opening_batch_id IS NOT NULL));

CREATE INDEX idx_bucket_consumption_opening_batch
    ON benefit_bucket_consumptions (opening_batch_id)
    WHERE source_type = 'OPENING_IMPORT';

-- The member's own view of what an import charged them, which is the read a
-- correction starts from.
CREATE INDEX idx_bucket_consumption_opening_member
    ON benefit_bucket_consumptions (member_id, policy_id, period_start, period_end)
    WHERE source_type = 'OPENING_IMPORT';

-- A correction must belong to a DIFFERENT batch than the movement it
-- corrects. Deliberately unlike a pre-authorization release, which inherits
-- its reservation's assignment: a release is part of the same act as its
-- hold, while a correction is a new decision made later, by someone, for a
-- stated reason. Filing it under the original batch would erase the fact that
-- a human intervened, and leave the batch's own reason describing two
-- different acts.
CREATE OR REPLACE FUNCTION validate_opening_correction_batch()
RETURNS trigger AS $$
DECLARE original_batch BIGINT;
BEGIN
    IF NEW.status = 'REVERSED' AND NEW.source_type = 'OPENING_IMPORT' THEN
        SELECT opening_batch_id INTO original_batch
          FROM benefit_bucket_consumptions WHERE id = NEW.reversal_of_id;
        IF original_batch IS NOT DISTINCT FROM NEW.opening_batch_id THEN
            RAISE EXCEPTION
                'An opening correction must be recorded as its own batch, not the batch it corrects (target %)',
                NEW.reversal_of_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_opening_correction_batch
BEFORE INSERT ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION validate_opening_correction_batch();
