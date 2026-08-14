-- ============================================================
-- V178: one snapshot row per (line x limit scope), carrying BOTH dimensions.
--
-- V176 required exactly one measure per row. That was wrong about the domain:
-- a single bucket is one commercial constraint that can cap money AND
-- occurrences at the same time --
--
--     amountLimit = 1000
--     timesLimit  = 3
--
-- Splitting it into two rows would suggest two independent limits, and would
-- complicate the snapshot, the audit trail, the idempotency keys and the
-- release path for no gain. The consumption ledger already carries
-- approved_amount and times_consumed on one row; the snapshot now matches it.
--
-- The dimensions stay strictly separate in ARITHMETIC even while sharing a
-- row: each has its own limit, its own consumed figure, its own remaining and
-- reservable figures, and its own bound. They are never added or compared --
-- a visit count and a currency amount are not the same kind of number.
--
-- The existing amount columns keep their names rather than being renamed to
-- effective_amount_limit and so on: they are already constrained by V175/V176
-- CHECKs that reference them, and renaming would rewrite those rules for a
-- clarity gain the comments here provide anyway.
-- ============================================================

-- ── 1. One measure per row was the wrong rule ────────────────────────────
ALTER TABLE preauth_line_limit_snapshots
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_one_measure,
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_unit_matches_basis,
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_within_available,
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_actual_remaining,
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_reservable;

-- ── 2. The occurrence dimension, alongside the monetary one ──────────────
-- Null throughout means "this bucket declares no occurrence limit" -- which
-- is not the same as zero, and zero is not the same as unconstrained.
ALTER TABLE preauth_line_limit_snapshots
    ADD COLUMN IF NOT EXISTS times_limit                    INTEGER,
    ADD COLUMN IF NOT EXISTS committed_times_before         INTEGER,
    ADD COLUMN IF NOT EXISTS reserved_times_before          INTEGER,
    ADD COLUMN IF NOT EXISTS actual_remaining_times_before  INTEGER,
    ADD COLUMN IF NOT EXISTS reservable_times_before        INTEGER;

-- A bucket may cap occurrences without capping money, so the monetary
-- figures become optional too.
ALTER TABLE preauth_line_limit_snapshots
    ALTER COLUMN effective_limit DROP NOT NULL,
    ALTER COLUMN committed_before DROP NOT NULL,
    ALTER COLUMN actual_remaining_before DROP NOT NULL,
    ALTER COLUMN reservable_available_before DROP NOT NULL;

-- ── 3. Each dimension is internally consistent, independently ────────────
ALTER TABLE preauth_line_limit_snapshots
    -- A row must constrain SOMETHING. A snapshot recording neither a monetary
    -- nor an occurrence ceiling explains nothing about why the decision came
    -- out as it did.
    ADD CONSTRAINT chk_preauth_limit_snapshot_has_a_dimension CHECK (
        effective_limit IS NOT NULL OR times_limit IS NOT NULL),

    -- The monetary dimension: present in full or absent in full.
    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_shape CHECK (
        (effective_limit IS NULL
            AND committed_before IS NULL
            AND actual_remaining_before IS NULL
            AND reservable_available_before IS NULL
            AND COALESCE(amount_reserved, 0) = 0)
        OR
        (effective_limit IS NOT NULL
            AND committed_before IS NOT NULL
            AND actual_remaining_before IS NOT NULL
            AND reservable_available_before IS NOT NULL)
    ),

    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_arithmetic CHECK (
        effective_limit IS NULL
        OR (actual_remaining_before = GREATEST(0, effective_limit - committed_before)
            AND reservable_available_before
                = GREATEST(0, actual_remaining_before - reserved_before))
    ),

    -- A decision may never hold more money than was available to hold.
    ADD CONSTRAINT chk_preauth_limit_snapshot_amount_bound CHECK (
        amount_reserved IS NULL
        OR reservable_available_before IS NULL
        OR amount_reserved <= reservable_available_before),

    -- The occurrence dimension: same rules, its own numbers.
    ADD CONSTRAINT chk_preauth_limit_snapshot_times_shape CHECK (
        (times_limit IS NULL
            AND committed_times_before IS NULL
            AND actual_remaining_times_before IS NULL
            AND reservable_times_before IS NULL
            AND COALESCE(times_reserved, 0) = 0)
        OR
        (times_limit IS NOT NULL
            AND committed_times_before IS NOT NULL
            AND actual_remaining_times_before IS NOT NULL
            AND reservable_times_before IS NOT NULL)
    ),

    ADD CONSTRAINT chk_preauth_limit_snapshot_times_arithmetic CHECK (
        times_limit IS NULL
        OR (actual_remaining_times_before = GREATEST(0, times_limit - committed_times_before)
            AND reservable_times_before
                = GREATEST(0, actual_remaining_times_before - COALESCE(reserved_times_before, 0)))
    ),

    ADD CONSTRAINT chk_preauth_limit_snapshot_times_bound CHECK (
        times_reserved IS NULL
        OR reservable_times_before IS NULL
        OR times_reserved <= reservable_times_before),

    -- ── 4. The general ceiling counts money only ─────────────────────────
    -- It is a synthetic scope with no bucket behind it, and no occurrence
    -- limit exists for it today. Allowing one would invent a rule.
    ADD CONSTRAINT chk_preauth_limit_snapshot_general_is_monetary CHECK (
        limit_scope <> 'POLICY_GENERAL'
        OR (bucket_id IS NULL
            AND times_limit IS NULL
            AND COALESCE(times_reserved, 0) = 0
            AND COALESCE(days_reserved, 0) = 0)
    ),

    -- ── 5. Days are not reservable ───────────────────────────────────────
    -- A day limit counts distinct service dates, and a pre-authorization
    -- carries one expected date with no admission or discharge behind it.
    -- The decision builder refuses such a bucket outright; this makes it
    -- impossible for a row to CLAIM a day was held when none could be.
    ADD CONSTRAINT chk_preauth_limit_snapshot_no_day_reservation CHECK (
        COALESCE(days_reserved, 0) = 0);

-- ── 6. reserved_unit no longer decides which measure is present ──────────
-- It described a row that carried exactly one measure. A row may now carry
-- two, so the column records the basis of the MONETARY figure only, and is
-- irrelevant when there is none.
COMMENT ON COLUMN preauth_line_limit_snapshots.reserved_unit IS
    'Unit of the monetary measure only (CURRENCY). The occurrence dimension is '
    'always counted in occurrences and needs no unit column. Retained for '
    'readability; it no longer determines which measures a row may carry.';

COMMENT ON COLUMN preauth_line_limit_snapshots.amount_reserved IS
    'Money held against this scope. May be present alongside times_reserved: '
    'one bucket can cap both. Never added to it.';

COMMENT ON COLUMN preauth_line_limit_snapshots.times_reserved IS
    'Occurrences held against this scope. A count, never a currency amount.';
