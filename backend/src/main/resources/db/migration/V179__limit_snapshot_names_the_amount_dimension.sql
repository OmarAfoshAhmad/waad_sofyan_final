-- ============================================================
-- V179: stop describing a count-only ceiling as if it measured money.
--
-- A bucket that caps only visits was being recorded with
-- consumption_basis='COMPANY_SHARE' and reserved_unit='CURRENCY' while
-- amount_reserved stayed null -- because both columns were NOT NULL and
-- something had to go in them. The arithmetic was unaffected, which is
-- exactly what makes it dangerous: a snapshot is read years later by someone
-- reconstructing why a decision came out as it did, and this one asserts a
-- monetary basis that never existed. A false audit trail is worse than a
-- missing one, because nothing signals that it is wrong.
--
-- The fix is not a NONE enum value. Adding one would keep the columns
-- mandatory and turn "there is no monetary dimension" into a kind of monetary
-- basis -- the same lie in a different word. Instead the columns are named
-- for the dimension they describe and are simply ABSENT when it is.
--
--     consumption_basis -> amount_consumption_basis
--     reserved_unit     -> amount_unit
--
-- The occurrence dimension needs no unit column: its fields are already
-- unambiguous counts, and a column that can only ever hold 'TIMES' records
-- nothing. count_unit is deliberately not added.
-- ============================================================

ALTER TABLE preauth_line_limit_snapshots
    RENAME COLUMN consumption_basis TO amount_consumption_basis;

ALTER TABLE preauth_line_limit_snapshots
    RENAME COLUMN reserved_unit TO amount_unit;

-- Absent, not invented, when the ceiling measures no money.
ALTER TABLE preauth_line_limit_snapshots
    ALTER COLUMN amount_consumption_basis DROP NOT NULL,
    ALTER COLUMN amount_unit DROP NOT NULL;

-- Rows written before this migration all carried a real monetary dimension
-- (V178 only made the monetary columns optional; nothing had yet written a
-- count-only row), so there is nothing to reclassify. Prove it rather than
-- assume it: a row describing money it never measured must not survive.
DO $$
DECLARE
    mislabelled BIGINT;
BEGIN
    SELECT COUNT(*) INTO mislabelled
    FROM preauth_line_limit_snapshots
    WHERE effective_limit IS NULL
      AND (amount_consumption_basis IS NOT NULL OR amount_unit IS NOT NULL);

    IF mislabelled > 0 THEN
        RAISE EXCEPTION
            'V179 aborted: % snapshot row(s) name a monetary basis for a ceiling that '
            'measures no money. Reclassify them explicitly before migrating.', mislabelled;
    END IF;
END $$;

ALTER TABLE preauth_line_limit_snapshots
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_basis,
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_unit;

ALTER TABLE preauth_line_limit_snapshots
    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_basis CHECK (
        amount_consumption_basis IS NULL
        OR amount_consumption_basis IN ('ELIGIBLE_AMOUNT', 'COMPANY_SHARE')),

    -- Money is measured in currency and nothing else. TIMES and DAYS were
    -- values this column could take only because it once described whichever
    -- single measure the row carried; a row may now carry two, and this one
    -- describes the monetary measure alone.
    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_unit CHECK (
        amount_unit IS NULL OR amount_unit = 'CURRENCY'),

    -- The monetary descriptors and the monetary figures appear together or
    -- not at all. A basis without an amount describes nothing; an amount
    -- without a basis cannot be reproduced.
    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_described CHECK (
        (effective_limit IS NULL
            AND amount_consumption_basis IS NULL
            AND amount_unit IS NULL)
        OR
        (effective_limit IS NOT NULL
            AND amount_consumption_basis IS NOT NULL
            AND amount_unit = 'CURRENCY')
    ),

    -- An amount may only be held where a monetary ceiling exists.
    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_needs_basis CHECK (
        amount_reserved IS NULL OR amount_consumption_basis IS NOT NULL);

-- Note on what is deliberately still allowed: a row with BOTH holds at zero.
-- That is the exhausted-ceiling snapshot, and it is the whole record of why a
-- decision granted nothing. The ledger stays empty for it -- no movement
-- happened -- but the explanation must survive. The existing
-- chk_preauth_limit_snapshot_has_a_dimension already requires the row to
-- declare at least one CEILING, which is the real "row records nothing" case.

COMMENT ON COLUMN preauth_line_limit_snapshots.amount_consumption_basis IS
    'What the monetary ceiling measures (ELIGIBLE_AMOUNT or COMPANY_SHARE). '
    'NULL when this ceiling constrains occurrences only and measures no money.';

COMMENT ON COLUMN preauth_line_limit_snapshots.amount_unit IS
    'Always CURRENCY when a monetary dimension is present, NULL otherwise. '
    'The occurrence dimension needs no unit column: its fields are counts.';
