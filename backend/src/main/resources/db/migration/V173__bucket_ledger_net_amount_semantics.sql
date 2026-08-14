-- ============================================================
-- V173: make benefit_bucket_consumptions a genuine append-only ledger.
--
-- The table has always been DESCRIBED as "append-only reversible bucket
-- ledger" (V84's own comment), but it was not one. Reversal worked by
-- MUTATING the original row:
--
--     original.setStatus(REVERSED); save(original);   // <- the balance effect
--     save(new REVERSED marker row);                  // <- a marker only
--
-- and every balance query filters on status:
--     aggregateAmountBalances : status IN ('COMMITTED','RESERVED')
--     sumCommittedAmount      : status = 'COMMITTED'
--
-- so REVERSED rows are excluded entirely. The money was released ONLY
-- because the original stopped matching the filter. The compensating row
-- carried no arithmetic weight at all, and the history no longer showed that
-- the amount had ever been committed.
--
-- From here the original row is immutable and the compensating row does the
-- work:
--
--     net(original) = original.amount - SUM(amount of its reversals)
--
-- This supports what the old model could not express: PARTIAL reversal,
-- partial release, and converting a 500 reservation into a 350 actual
-- consumption (release 500, commit 350, 150 returns to available) without
-- inventing negative amounts.
--
-- Ordering matters: legacy rows are normalized FIRST, and only then are the
-- immutability triggers installed. Installing them earlier would make the
-- normalization itself impossible.
-- ============================================================

-- ── 1. Explicit meaning for every movement ───────────────────────────────
-- `status` already carries the ENTRY TYPE (RESERVED | COMMITTED | REVERSED)
-- and is referenced by name in many queries, so it keeps its column name
-- rather than being renamed to entry_type; source_type is what it was
-- missing. The two were previously conflated: every row was implicitly a
-- claim consumption, so an opening imported balance had to be faked as a
-- claim and a reservation could not be expressed at all.
ALTER TABLE benefit_bucket_consumptions
    ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(30),
    ADD COLUMN IF NOT EXISTS source_type     VARCHAR(20);

UPDATE benefit_bucket_consumptions SET source_type = 'CLAIM' WHERE source_type IS NULL;
ALTER TABLE benefit_bucket_consumptions ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_bucket_consumption_source_type
        CHECK (source_type IN ('CLAIM', 'PREAUTH', 'OPENING_IMPORT', 'ADJUSTMENT'));

-- ── 2. Audit the legacy rows BEFORE touching anything ────────────────────
-- A legacy reversal is: an original whose status was flipped to REVERSED,
-- plus a marker row pointing at it (reversal_of_id) that is also REVERSED.
-- Anything that does not fit that shape is not guessed at -- the migration
-- fails with a report, because silently "fixing" financial rows we do not
-- understand is worse than stopping.
DO $$
DECLARE
    orphan_markers   BIGINT;
    unmarked_originals BIGINT;
BEGIN
    -- A compensating row pointing at nothing.
    SELECT COUNT(*) INTO orphan_markers
    FROM benefit_bucket_consumptions c
    WHERE c.status = 'REVERSED' AND c.reversal_of_id IS NULL;

    -- An original that was flipped to REVERSED but has no compensating row,
    -- so after normalization its release would silently disappear.
    SELECT COUNT(*) INTO unmarked_originals
    FROM benefit_bucket_consumptions o
    WHERE o.status = 'REVERSED'
      AND o.reversal_of_id IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM benefit_bucket_consumptions r WHERE r.reversal_of_id = o.id);

    IF orphan_markers > 0 AND unmarked_originals > 0 THEN
        RAISE EXCEPTION
            'V173 aborted: % reversed row(s) have no compensating movement and cannot be normalized safely. '
            'Resolve them explicitly before migrating.', unmarked_originals;
    END IF;
END $$;

-- ── 3. Normalize: restore flipped originals, keep the marker as the
--        compensating movement. Net becomes exactly zero, so no balance
--        changes as a result of this migration.
UPDATE benefit_bucket_consumptions o
SET status = 'COMMITTED'
WHERE o.status = 'REVERSED'
  AND o.reversal_of_id IS NULL
  AND EXISTS (SELECT 1 FROM benefit_bucket_consumptions r WHERE r.reversal_of_id = o.id);

UPDATE benefit_bucket_consumptions
SET reversal_reason = 'CLAIM_REVERSAL'
WHERE status = 'REVERSED' AND reversal_of_id IS NOT NULL AND reversal_reason IS NULL;

-- Verify the normalization did not change any balance: every normalized
-- original must now net to zero against its compensating rows.
DO $$
DECLARE
    mismatched BIGINT;
BEGIN
    SELECT COUNT(*) INTO mismatched
    FROM benefit_bucket_consumptions o
    JOIN (
        SELECT reversal_of_id, SUM(approved_amount) AS reversed_amount
        FROM benefit_bucket_consumptions
        WHERE status = 'REVERSED' AND reversal_of_id IS NOT NULL
        GROUP BY reversal_of_id
    ) r ON r.reversal_of_id = o.id
    WHERE o.approved_amount <> r.reversed_amount;

    IF mismatched > 0 THEN
        RAISE EXCEPTION
            'V173 aborted: % legacy original(s) do not net to zero against their reversals. '
            'Normalizing them would change a balance.', mismatched;
    END IF;
END $$;

-- ── 4. Structural rules for compensating movements ───────────────────────
ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_bucket_consumption_reversal_reason
        CHECK (reversal_reason IS NULL OR reversal_reason IN
            ('PREAUTH_RELEASE', 'PREAUTH_EXPIRY', 'PREAUTH_CANCELLATION',
             'CLAIM_REVERSAL', 'CLAIM_CORRECTION')),
    -- A REVERSED row is always a compensating movement, never a standalone
    -- state. This is the rule that makes "net = original - reversals"
    -- well-defined.
    ADD CONSTRAINT chk_bucket_consumption_reversal_shape CHECK (
        (status = 'REVERSED' AND reversal_of_id IS NOT NULL AND reversal_reason IS NOT NULL)
        OR
        (status <> 'REVERSED' AND reversal_of_id IS NULL)
    );

-- A CHECK cannot look at the referenced row, and cannot sum sibling rows, so
-- the cross-row rules are a trigger: the target must be a real original, the
-- compensation must describe the same measurement, and the reversals of one
-- original may never exceed it.
CREATE OR REPLACE FUNCTION validate_bucket_consumption_reversal()
RETURNS trigger AS $$
DECLARE
    original benefit_bucket_consumptions%ROWTYPE;
    already_reversed NUMERIC(15,2);
BEGIN
    IF NEW.status <> 'REVERSED' THEN
        RETURN NEW;
    END IF;

    SELECT * INTO original FROM benefit_bucket_consumptions WHERE id = NEW.reversal_of_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Reversal target % does not exist', NEW.reversal_of_id;
    END IF;

    -- Reversing a reversal would make "net" recursive and meaningless.
    IF original.status = 'REVERSED' THEN
        RAISE EXCEPTION 'A compensating movement cannot itself be reversed (target %)', NEW.reversal_of_id;
    END IF;

    IF original.member_id IS DISTINCT FROM NEW.member_id
       OR original.policy_id IS DISTINCT FROM NEW.policy_id
       OR original.bucket_id IS DISTINCT FROM NEW.bucket_id
       OR original.period_start IS DISTINCT FROM NEW.period_start
       OR original.period_end IS DISTINCT FROM NEW.period_end THEN
        RAISE EXCEPTION 'A compensating movement must describe the same member, policy, bucket and period as its original (target %)',
            NEW.reversal_of_id;
    END IF;

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

DROP TRIGGER IF EXISTS trg_validate_bucket_consumption_reversal ON benefit_bucket_consumptions;
CREATE TRIGGER trg_validate_bucket_consumption_reversal
BEFORE INSERT ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION validate_bucket_consumption_reversal();

-- ── 5. Immutability, installed LAST so the normalization above could run ──
CREATE OR REPLACE FUNCTION prevent_bucket_consumption_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'benefit_bucket_consumptions is append-only: % is not allowed. '
        'Post a compensating movement instead of editing a posted one.', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_update_bucket_consumptions ON benefit_bucket_consumptions;
CREATE TRIGGER trg_no_update_bucket_consumptions
BEFORE UPDATE ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION prevent_bucket_consumption_mutation();

DROP TRIGGER IF EXISTS trg_no_delete_bucket_consumptions ON benefit_bucket_consumptions;
CREATE TRIGGER trg_no_delete_bucket_consumptions
BEFORE DELETE ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION prevent_bucket_consumption_mutation();

CREATE INDEX IF NOT EXISTS idx_bucket_consumption_reversal_of
    ON benefit_bucket_consumptions(reversal_of_id)
    WHERE reversal_of_id IS NOT NULL;
