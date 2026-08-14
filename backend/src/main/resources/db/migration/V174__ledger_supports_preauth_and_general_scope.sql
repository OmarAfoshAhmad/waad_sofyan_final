-- ============================================================
-- V174: teach the ledger to REPRESENT a pre-authorization hold and a
-- general-ceiling movement. Representation only -- nothing writes a
-- reservation yet. The first writer arrives with the approval service in
-- the next step; until then every balance must stay bit-for-bit identical.
--
-- Two structural facts blocked reservations entirely:
--
--   claim_id, claim_line_id  NOT NULL  -> a hold exists BEFORE any claim
--   bucket_id                NOT NULL  -> POLICY_GENERAL is a synthetic
--                                          scope with no bucket row at all
--
-- The second is the subtle one. LimitBalanceReader already handles the
-- general ceiling on a separate code path and carries an explicit note that
-- general reservations stay zero "until the pre-authorization reservation
-- ledger is connected; manufacturing them from bucket rows would double
-- count." One line can map to several buckets, so summing bucket rows to
-- obtain the general figure counts the same money repeatedly. The general
-- ceiling therefore needs its OWN rows, which needs bucket_id to be
-- optional, which needs an explicit scope to keep that optionality honest.
--
-- Note on the pre-authorization tables: two exist in this schema --
-- preauthorization_requests (no JPA entity, no writer) and
-- pre_authorizations (mapped by PreAuthorization, with pre_authorization_lines
-- FK'd to it, used by PreAuthorizationService). The live pair is the one
-- referenced here.
-- ============================================================

ALTER TABLE benefit_bucket_consumptions
    ADD COLUMN IF NOT EXISTS limit_scope     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS preauth_id      BIGINT REFERENCES pre_authorizations(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS preauth_line_id BIGINT REFERENCES pre_authorization_lines(id) ON DELETE RESTRICT;

-- Every existing row is a claim-sourced BUCKET movement. This is a
-- representation backfill, NOT a financial migration: no general-ceiling row
-- is invented, because none ever existed.
--
-- V173 installed the append-only triggers, which refuse every UPDATE -- that
-- is the whole point of them, and V173 deliberately installed them LAST so its
-- own normalization could run first. This migration needs the same room: a
-- classification column added today has no value on rows written yesterday,
-- and only an UPDATE can give it one.
--
-- The suspension is safe and is kept as narrow as possible. The statement
-- touches limit_scope alone -- a label that carries no money. No amount, no
-- status, no reversal link, no period is altered, so no balance can move. The
-- trigger is restored immediately afterwards and verified below; Postgres runs
-- DDL transactionally, so a failure anywhere in this migration rolls the
-- suspension back with everything else.
ALTER TABLE benefit_bucket_consumptions DISABLE TRIGGER trg_no_update_bucket_consumptions;

UPDATE benefit_bucket_consumptions SET limit_scope = 'BUCKET' WHERE limit_scope IS NULL;

ALTER TABLE benefit_bucket_consumptions ENABLE TRIGGER trg_no_update_bucket_consumptions;

-- Leaving the ledger mutable would silently undo V173. Prove it did not.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_no_update_bucket_consumptions'
          AND tgrelid = 'benefit_bucket_consumptions'::regclass
          AND tgenabled <> 'D'
    ) THEN
        RAISE EXCEPTION
            'V174 aborted: the append-only UPDATE guard was not restored. '
            'The ledger must not be left mutable.';
    END IF;
END $$;

-- Refuse to proceed if any existing row cannot be classified -- a row with no
-- bucket could not have been a BUCKET movement, and inventing a scope for it
-- would be a guess about money.
DO $$
DECLARE
    unclassifiable BIGINT;
BEGIN
    SELECT COUNT(*) INTO unclassifiable
    FROM benefit_bucket_consumptions
    WHERE bucket_id IS NULL OR claim_id IS NULL OR claim_line_id IS NULL;

    IF unclassifiable > 0 THEN
        RAISE EXCEPTION
            'V174 aborted: % existing row(s) cannot be classified as CLAIM+BUCKET. '
            'Classify them explicitly before migrating.', unclassifiable;
    END IF;
END $$;

ALTER TABLE benefit_bucket_consumptions ALTER COLUMN limit_scope SET NOT NULL;

-- Only now may these become optional: the audit above proved every current
-- row still satisfies the CLAIM+BUCKET shape the constraints below demand.
ALTER TABLE benefit_bucket_consumptions
    ALTER COLUMN claim_id DROP NOT NULL,
    ALTER COLUMN claim_line_id DROP NOT NULL,
    ALTER COLUMN bucket_id DROP NOT NULL;

-- The entry-type matrix below is an accounting rule, so a row that breaks it
-- is a financial fact we do not understand -- not something to force through.
-- V173 backfilled every legacy row to source_type CLAIM, so a legacy RESERVED
-- row would land as CLAIM+RESERVED, which the matrix forbids. Report it and
-- stop rather than letting Postgres fail with an opaque constraint error that
-- names no row.
DO $$
DECLARE
    offending BIGINT;
    sample TEXT;
BEGIN
    SELECT COUNT(*) INTO offending
    FROM benefit_bucket_consumptions
    WHERE NOT (
        (source_type = 'CLAIM'          AND status IN ('COMMITTED', 'REVERSED'))
        OR (source_type = 'PREAUTH'        AND status IN ('RESERVED', 'REVERSED'))
        OR (source_type = 'OPENING_IMPORT' AND status IN ('COMMITTED', 'REVERSED'))
        OR (source_type = 'ADJUSTMENT'     AND status IN ('COMMITTED', 'REVERSED'))
    );

    IF offending > 0 THEN
        SELECT string_agg(DISTINCT source_type || '+' || status, ', ')
        INTO sample
        FROM benefit_bucket_consumptions
        WHERE NOT (
            (source_type = 'CLAIM'          AND status IN ('COMMITTED', 'REVERSED'))
            OR (source_type = 'PREAUTH'        AND status IN ('RESERVED', 'REVERSED'))
            OR (source_type = 'OPENING_IMPORT' AND status IN ('COMMITTED', 'REVERSED'))
            OR (source_type = 'ADJUSTMENT'     AND status IN ('COMMITTED', 'REVERSED'))
        );

        RAISE EXCEPTION
            'V174 aborted: % row(s) break the source/entry-type matrix (%). '
            'Classify them explicitly before migrating.', offending, sample;
    END IF;
END $$;

ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_bucket_consumption_limit_scope
        CHECK (limit_scope IN ('BUCKET', 'POLICY_GENERAL')),

    -- A BUCKET movement names its bucket; a general-ceiling movement must not
    -- pretend to have one. Without this, bucket_id being nullable would let a
    -- general row carry a bucket and be double-counted by bucket queries.
    ADD CONSTRAINT chk_bucket_consumption_scope_bucket CHECK (
        (limit_scope = 'BUCKET' AND bucket_id IS NOT NULL)
        OR
        (limit_scope = 'POLICY_GENERAL' AND bucket_id IS NULL)
    ),

    -- Exactly one source, and a head is never present without its line.
    -- Checking only "one of the two ids is set" would allow a claim_id with
    -- no claim_line_id, which no aggregation could attribute.
    ADD CONSTRAINT chk_bucket_consumption_source_shape CHECK (
        (source_type = 'CLAIM'
            AND claim_id IS NOT NULL AND claim_line_id IS NOT NULL
            AND preauth_id IS NULL AND preauth_line_id IS NULL)
        OR
        (source_type = 'PREAUTH'
            AND preauth_id IS NOT NULL AND preauth_line_id IS NOT NULL
            AND claim_id IS NULL AND claim_line_id IS NULL)
        OR
        (source_type IN ('OPENING_IMPORT', 'ADJUSTMENT')
            AND claim_id IS NULL AND claim_line_id IS NULL
            AND preauth_id IS NULL AND preauth_line_id IS NULL)
    ),

    -- Which ENTRY TYPES each source may post. An explicit matrix, not a
    -- blanket allowance: the pairing is the accounting rule, and leaving it
    -- to the application means it survives only as long as whoever writes
    -- the next service remembers it.
    --
    --   CLAIM          COMMITTED, REVERSED   a claim consumes; it never holds
    --   PREAUTH        RESERVED,  REVERSED   a hold; the actual consumption is
    --                                        posted by the claim that follows
    --   OPENING_IMPORT COMMITTED, REVERSED   a balance carried in from a prior
    --                                        system is already spent
    --   ADJUSTMENT     COMMITTED, REVERSED   a manual correction to consumption
    --
    -- RESERVED is therefore reachable ONLY through PREAUTH: nothing but a
    -- pre-authorization may hold a member's limit. And PREAUTH + COMMITTED is
    -- now structurally impossible, which is what keeps the claim-scoped JPQL
    -- reads (they inner-join through claim_id) from silently dropping rows
    -- they were never meant to see.
    --
    -- REVERSED is permitted for every source because a compensating movement
    -- must mirror its original's source_type -- enforced row-to-row by
    -- validate_bucket_consumption_reversal, which a CHECK cannot express.
    ADD CONSTRAINT chk_bucket_consumption_entry_type_by_source CHECK (
        (source_type = 'CLAIM'          AND status IN ('COMMITTED', 'REVERSED'))
        OR
        (source_type = 'PREAUTH'        AND status IN ('RESERVED', 'REVERSED'))
        OR
        (source_type = 'OPENING_IMPORT' AND status IN ('COMMITTED', 'REVERSED'))
        OR
        (source_type = 'ADJUSTMENT'     AND status IN ('COMMITTED', 'REVERSED'))
    );

-- A line must belong to the head it is recorded against: pointing at
-- pre-authorization 7 while naming a line of pre-authorization 9 would pass
-- both foreign keys and still be meaningless. A CHECK cannot express this.
CREATE OR REPLACE FUNCTION validate_bucket_consumption_line_ownership()
RETURNS trigger AS $$
DECLARE
    owner_id BIGINT;
BEGIN
    IF NEW.preauth_line_id IS NOT NULL THEN
        SELECT pre_authorization_id INTO owner_id
        FROM pre_authorization_lines WHERE id = NEW.preauth_line_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Pre-authorization line % does not exist', NEW.preauth_line_id;
        END IF;
        IF owner_id IS DISTINCT FROM NEW.preauth_id THEN
            RAISE EXCEPTION 'Pre-authorization line % belongs to pre-authorization %, not %',
                NEW.preauth_line_id, owner_id, NEW.preauth_id;
        END IF;
    END IF;

    IF NEW.claim_line_id IS NOT NULL THEN
        SELECT claim_id INTO owner_id FROM claim_lines WHERE id = NEW.claim_line_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Claim line % does not exist', NEW.claim_line_id;
        END IF;
        IF owner_id IS DISTINCT FROM NEW.claim_id THEN
            RAISE EXCEPTION 'Claim line % belongs to claim %, not %',
                NEW.claim_line_id, owner_id, NEW.claim_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validate_bucket_consumption_line_ownership ON benefit_bucket_consumptions;
CREATE TRIGGER trg_validate_bucket_consumption_line_ownership
BEFORE INSERT ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION validate_bucket_consumption_line_ownership();

-- Extend V173's reversal validator: a compensating movement must also match
-- its original's SOURCE and SCOPE, not merely member/policy/bucket/period.
-- Releasing a pre-authorization hold with a claim-shaped row, or offsetting a
-- bucket movement against the general ceiling, would both otherwise pass.
CREATE OR REPLACE FUNCTION validate_bucket_consumption_reversal()
RETURNS trigger AS $$
DECLARE
    original benefit_bucket_consumptions%ROWTYPE;
    already_reversed NUMERIC(15,2);
BEGIN
    IF NEW.status <> 'REVERSED' THEN
        RETURN NEW;
    END IF;

    -- A BEFORE ROW trigger fires ahead of any CHECK constraint, so this runs
    -- before chk_bucket_consumption_reversal_shape can speak. Name the real
    -- error rather than letting the lookup below report a NULL id as a
    -- missing row -- an operator reading the log learns nothing from
    -- "target <NULL> does not exist".
    IF NEW.reversal_of_id IS NULL THEN
        RAISE EXCEPTION 'A REVERSED row must name the movement it compensates (reversal_of_id is null)';
    END IF;

    SELECT * INTO original FROM benefit_bucket_consumptions WHERE id = NEW.reversal_of_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Reversal target % does not exist', NEW.reversal_of_id;
    END IF;

    IF original.status = 'REVERSED' THEN
        RAISE EXCEPTION 'A compensating movement cannot itself be reversed (target %)', NEW.reversal_of_id;
    END IF;

    IF original.member_id IS DISTINCT FROM NEW.member_id
       OR original.policy_id IS DISTINCT FROM NEW.policy_id
       OR original.bucket_id IS DISTINCT FROM NEW.bucket_id
       OR original.period_start IS DISTINCT FROM NEW.period_start
       OR original.period_end IS DISTINCT FROM NEW.period_end
       OR original.limit_scope IS DISTINCT FROM NEW.limit_scope
       OR original.source_type IS DISTINCT FROM NEW.source_type THEN
        RAISE EXCEPTION 'A compensating movement must describe the same member, policy, scope, source and period as its original (target %)',
            NEW.reversal_of_id;
    END IF;

    -- FOR UPDATE above serializes concurrent reversals of the same original,
    -- so this total cannot be raced past the original's amount.
    SELECT COALESCE(SUM(approved_amount), 0) INTO already_reversed
    FROM benefit_bucket_consumptions
    WHERE reversal_of_id = NEW.reversal_of_id AND status = 'REVERSED';

    IF already_reversed + NEW.approved_amount > original.approved_amount THEN
        RAISE EXCEPTION 'Reversing % would exceed the original amount % (already reversed %)',
            NEW.approved_amount, original.approved_amount, already_reversed;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX IF NOT EXISTS idx_bucket_consumption_preauth
    ON benefit_bucket_consumptions(preauth_id, preauth_line_id)
    WHERE preauth_id IS NOT NULL;

-- Reading general-ceiling movements without touching bucket rows.
CREATE INDEX IF NOT EXISTS idx_bucket_consumption_general_scope
    ON benefit_bucket_consumptions(member_id, policy_id, period_start, period_end, status)
    WHERE limit_scope = 'POLICY_GENERAL';
