-- ============================================================
-- V177: the reviewer's decision becomes an INPUT, and a third coverage
-- outcome is named.
--
-- 1. A refusal is a judgement, not arithmetic.
--
-- The decision builder had no way to receive one, so it recorded zero refused
-- and let the outcome fall out of the numbers. That is backwards: whether a
-- service or a quantity was cut is something a reviewer decides, and the
-- engine must never manufacture a refusal to make its own totals balance.
--
-- 2. An exhausted ceiling is not "zero coverage".
--
-- When a bucket is spent, the policy still covers 80% -- what reached zero is
-- the payable amount, because the ceiling is gone. Showing a member "0%
-- coverage" misstates their policy. LIMIT_CAPPED (part of the value exceeded
-- the ceiling) and LIMIT_EXHAUSTED (nothing was left to pay from) are
-- different facts and are now named separately.
--
-- Still representation only: nothing writes a snapshot or a hold yet.
-- ============================================================

-- ── 1. The reviewer's decision, per line ─────────────────────────────────
ALTER TABLE pre_authorization_lines
    ADD COLUMN IF NOT EXISTS requested_quantity       INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS approved_quantity        INTEGER,
    ADD COLUMN IF NOT EXISTS explicit_rejected_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rejection_reason         VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS review_decision          VARCHAR(20);

ALTER TABLE pre_authorization_lines
    ADD CONSTRAINT chk_preauth_line_requested_quantity CHECK (requested_quantity > 0),

    ADD CONSTRAINT chk_preauth_line_approved_quantity CHECK (
        approved_quantity IS NULL
        OR (approved_quantity >= 0 AND approved_quantity <= requested_quantity)),

    ADD CONSTRAINT chk_preauth_line_explicit_rejected CHECK (explicit_rejected_amount >= 0),

    ADD CONSTRAINT chk_preauth_line_review_decision CHECK (
        review_decision IS NULL
        OR review_decision IN ('APPROVE', 'PARTIALLY_APPROVE', 'REJECT')),

    -- A refusal without a stated reason cannot be explained to the member or
    -- the provider, and cannot be appealed. Required for both the partial and
    -- the total case.
    ADD CONSTRAINT chk_preauth_line_rejection_reason CHECK (
        review_decision IS NULL
        OR review_decision = 'APPROVE'
        OR (rejection_reason IS NOT NULL AND length(trim(rejection_reason)) > 0)),

    -- The decision and the numbers must agree. APPROVE cannot sit next to a
    -- reduced quantity, and REJECT cannot sit next to an approved one --
    -- otherwise the label and the money tell different stories.
    ADD CONSTRAINT chk_preauth_line_decision_matches_quantity CHECK (
        review_decision IS NULL
        OR (review_decision = 'APPROVE'
                AND (approved_quantity IS NULL OR approved_quantity = requested_quantity)
                AND explicit_rejected_amount = 0)
        OR (review_decision = 'PARTIALLY_APPROVE'
                AND ((approved_quantity IS NOT NULL
                        AND approved_quantity > 0
                        AND approved_quantity < requested_quantity)
                     OR explicit_rejected_amount > 0))
        OR (review_decision = 'REJECT'
                AND (approved_quantity IS NULL OR approved_quantity = 0))
    );

-- ── 2. The third coverage outcome ────────────────────────────────────────
ALTER TABLE preauth_decision_snapshots
    DROP CONSTRAINT IF EXISTS chk_preauth_snapshot_coverage_outcome,
    DROP CONSTRAINT IF EXISTS chk_preauth_snapshot_capped_outcome;

ALTER TABLE preauth_decision_snapshots
    ADD CONSTRAINT chk_preauth_snapshot_coverage_outcome CHECK (
        coverage_outcome IN ('FULLY_COVERED', 'LIMIT_CAPPED', 'LIMIT_EXHAUSTED', 'PARTIALLY_COVERED')),

    -- Both ceiling outcomes must be backed by a real excess; claiming a
    -- ceiling was reached without one would misstate why the insurer paid
    -- less.
    ADD CONSTRAINT chk_preauth_snapshot_capped_outcome CHECK (
        coverage_outcome NOT IN ('LIMIT_CAPPED', 'LIMIT_EXHAUSTED') OR limit_excess_total > 0),

    -- Exhausted means the insurer pays nothing at all. Distinguishing it from
    -- CAPPED by the company share is what keeps the two from blurring: capped
    -- still pays something.
    ADD CONSTRAINT chk_preauth_snapshot_exhausted_pays_nothing CHECK (
        coverage_outcome <> 'LIMIT_EXHAUSTED' OR company_share_total = 0);

-- ── 3. The line snapshot records the reviewer's decision too ─────────────
ALTER TABLE preauth_line_snapshots
    ADD COLUMN IF NOT EXISTS requested_quantity INTEGER,
    ADD COLUMN IF NOT EXISTS approved_quantity  INTEGER,
    ADD COLUMN IF NOT EXISTS review_decision    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS rejection_reason   VARCHAR(1000);
