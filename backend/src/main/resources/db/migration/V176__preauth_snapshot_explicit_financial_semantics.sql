-- ============================================================
-- V176: separate what a snapshot MEANS from what it happened to hold.
--
-- V175 named one column approved_total and made PARTIALLY_APPROVED mean
-- "approved_total < requested_total". Both were wrong, and they were wrong
-- together.
--
-- approved_total carried three different meanings at once: the value of the
-- service authorised, the settlement after the contractual discount, and the
-- insurer's commitment. They are different numbers. The financial
-- constitution's S13 invariant distributes the ENTIRE requested amount across
-- the parties, so a total built from the shares always equals the request --
-- which made "approved < requested" unreachable, and silently classified a
-- limit-capped decision as a full approval.
--
-- The deeper error was conflating two questions a reviewer and a patient ask
-- separately:
--
--   was the SERVICE approved?      -- was any part of it refused?
--   did the INSURER cover it all?  -- or did a ceiling cap its share?
--
-- A service can be fully approved while the insurer covers only part of its
-- value. Telling a patient their pre-authorization is "partially approved"
-- when the whole service was authorised -- merely with a ceiling reached --
-- misdescribes their entitlement. So PreAuthStatus keeps its clinical/
-- administrative meaning, and a separate coverage_outcome carries the
-- financial one.
--
-- The limit snapshot gains the same honesty about UNITS. A times limit
-- counts visits; a days limit counts days; an amount limit counts money.
-- Storing a currency figure in a times limit, or comparing the two, is
-- meaningless -- so each scope now declares its basis and reserves in its own
-- unit. These are never summed across scopes.
--
-- Safe to restate rather than patch: no writer exists yet, so these tables
-- are empty everywhere.
-- ============================================================

-- ── 1. Name the amounts for what they are ────────────────────────────────
ALTER TABLE preauth_decision_snapshots
    RENAME COLUMN approved_total TO settlement_total;

ALTER TABLE preauth_decision_snapshots
    -- The value of the service actually authorised: what was asked, less
    -- what was explicitly refused. Not reduced by a ceiling -- a ceiling
    -- limits who PAYS, not what was authorised.
    ADD COLUMN IF NOT EXISTS authorized_service_total NUMERIC(15,2)
        NOT NULL DEFAULT 0 CHECK (authorized_service_total >= 0),

    -- The provider's contractual discount, in its own field rather than
    -- buried in a net figure -- its size depends on whether it applied
    -- before or after the refusal, which discount_before_rejection records.
    ADD COLUMN IF NOT EXISTS provider_discount_total NUMERIC(15,2)
        NOT NULL DEFAULT 0 CHECK (provider_discount_total >= 0),

    -- The part of the service value that fell outside the benefit ceiling
    -- and therefore became the patient's responsibility (S6-S7).
    ADD COLUMN IF NOT EXISTS limit_excess_total NUMERIC(15,2)
        NOT NULL DEFAULT 0 CHECK (limit_excess_total >= 0),

    ADD COLUMN IF NOT EXISTS limit_capped BOOLEAN NOT NULL DEFAULT false,

    -- The FINANCIAL outcome, kept apart from decision_status so neither has
    -- to carry the other's meaning.
    ADD COLUMN IF NOT EXISTS coverage_outcome VARCHAR(20);

UPDATE preauth_decision_snapshots SET coverage_outcome = 'FULLY_COVERED' WHERE coverage_outcome IS NULL;
ALTER TABLE preauth_decision_snapshots ALTER COLUMN coverage_outcome SET NOT NULL;

-- ── 2. Replace the meaning-laden constraints ─────────────────────────────
ALTER TABLE preauth_decision_snapshots
    DROP CONSTRAINT IF EXISTS chk_preauth_snapshot_partial,
    DROP CONSTRAINT IF EXISTS chk_preauth_snapshot_totals,
    DROP CONSTRAINT IF EXISTS chk_preauth_snapshot_shares;

ALTER TABLE preauth_decision_snapshots
    -- The settlement is exactly what the two parties pay between them.
    ADD CONSTRAINT chk_preauth_snapshot_settlement CHECK (
        settlement_total = patient_share_total + company_share_total),

    -- Nothing may be authorised beyond what was requested, and what was
    -- authorised plus what was refused cannot exceed it either.
    ADD CONSTRAINT chk_preauth_snapshot_authorized CHECK (
        authorized_service_total + rejected_total <= requested_total),

    -- The parties cannot settle more than was billed. Dropping the old
    -- approved_total rules removed this bound with them, and without it a
    -- snapshot could promise a provider more than the request it answers.
    ADD CONSTRAINT chk_preauth_snapshot_settlement_bound CHECK (
        settlement_total <= requested_total),

    ADD CONSTRAINT chk_preauth_snapshot_coverage_outcome CHECK (
        coverage_outcome IN ('FULLY_COVERED', 'LIMIT_CAPPED', 'PARTIALLY_COVERED')),

    -- The flag and the amount say the same thing, so they may never disagree.
    ADD CONSTRAINT chk_preauth_snapshot_limit_capped CHECK (
        limit_capped = (limit_excess_total > 0)),

    -- A capped outcome must be backed by a real excess; claiming a ceiling
    -- was reached without one would misstate why the insurer paid less.
    ADD CONSTRAINT chk_preauth_snapshot_capped_outcome CHECK (
        coverage_outcome <> 'LIMIT_CAPPED' OR limit_excess_total > 0),

    -- PARTIALLY_APPROVED is now about the SERVICE: something was explicitly
    -- refused. A ceiling alone never produces it -- that is what
    -- coverage_outcome is for.
    ADD CONSTRAINT chk_preauth_snapshot_partial_means_refusal CHECK (
        (decision_status = 'PARTIALLY_APPROVED' AND rejected_total > 0)
        OR
        (decision_status = 'APPROVED' AND rejected_total = 0)
    );

-- ── 3. Each scope reserves in its own unit ───────────────────────────────
ALTER TABLE preauth_line_limit_snapshots
    RENAME COLUMN reserved_amount TO amount_reserved;

ALTER TABLE preauth_line_limit_snapshots
    -- What this scope MEASURES. The general ceiling counts the insurer's
    -- money; a service bucket may count the eligible amount; a visit bucket
    -- counts occurrences. One decision, several independent measures.
    ADD COLUMN IF NOT EXISTS consumption_basis VARCHAR(20),
    ADD COLUMN IF NOT EXISTS reserved_unit VARCHAR(10),
    ADD COLUMN IF NOT EXISTS times_reserved INTEGER CHECK (times_reserved IS NULL OR times_reserved >= 0),
    ADD COLUMN IF NOT EXISTS days_reserved INTEGER CHECK (days_reserved IS NULL OR days_reserved >= 0);

UPDATE preauth_line_limit_snapshots
SET consumption_basis = COALESCE(consumption_basis, 'COMPANY_SHARE'),
    reserved_unit = COALESCE(reserved_unit, 'CURRENCY')
WHERE consumption_basis IS NULL OR reserved_unit IS NULL;

ALTER TABLE preauth_line_limit_snapshots
    ALTER COLUMN consumption_basis SET NOT NULL,
    ALTER COLUMN reserved_unit SET NOT NULL,
    ALTER COLUMN amount_reserved DROP NOT NULL;

ALTER TABLE preauth_line_limit_snapshots
    ADD CONSTRAINT chk_preauth_limit_snapshot_basis CHECK (
        consumption_basis IN ('ELIGIBLE_AMOUNT', 'COMPANY_SHARE', 'TIMES', 'DAYS')),

    ADD CONSTRAINT chk_preauth_limit_snapshot_unit CHECK (
        reserved_unit IN ('CURRENCY', 'TIMES', 'DAYS')),

    -- The unit and the basis must agree, and the reservation must be
    -- expressed in that unit and no other. This is what stops a currency
    -- figure being written into a visit count -- a number that would then be
    -- compared against a limit measured in visits.
    ADD CONSTRAINT chk_preauth_limit_snapshot_unit_matches_basis CHECK (
        (consumption_basis IN ('ELIGIBLE_AMOUNT', 'COMPANY_SHARE') AND reserved_unit = 'CURRENCY')
        OR (consumption_basis = 'TIMES' AND reserved_unit = 'TIMES')
        OR (consumption_basis = 'DAYS' AND reserved_unit = 'DAYS')
    ),

    ADD CONSTRAINT chk_preauth_limit_snapshot_one_measure CHECK (
        (reserved_unit = 'CURRENCY'
            AND amount_reserved IS NOT NULL AND times_reserved IS NULL AND days_reserved IS NULL)
        OR (reserved_unit = 'TIMES'
            AND times_reserved IS NOT NULL AND amount_reserved IS NULL AND days_reserved IS NULL)
        OR (reserved_unit = 'DAYS'
            AND days_reserved IS NOT NULL AND amount_reserved IS NULL AND times_reserved IS NULL)
    );

-- The old rule compared the reservation to the available balance. It still
-- holds, but only within a unit -- so it is restated against whichever
-- measure this row actually uses.
ALTER TABLE preauth_line_limit_snapshots
    DROP CONSTRAINT IF EXISTS chk_preauth_limit_snapshot_within_available;

ALTER TABLE preauth_line_limit_snapshots
    ADD CONSTRAINT chk_preauth_limit_snapshot_within_available CHECK (
        COALESCE(amount_reserved, times_reserved, days_reserved) <= reservable_available_before);

-- ── 4. The line's own medical identity ───────────────────────────────────
-- A pre-authorization can carry several lines from different categories, and
-- the benefit rule that decides which buckets apply is resolved from the
-- CATEGORY. Resolving it from the head means every line of a mixed request
-- is priced against one category -- the wrong buckets for all but one of
-- them. provider_service_id identifies the provider's own catalogue entry
-- and its price; it is not a medical classification and cannot stand in for
-- one.
ALTER TABLE pre_authorization_lines
    ADD COLUMN IF NOT EXISTS medical_service_id  BIGINT,
    ADD COLUMN IF NOT EXISTS medical_category_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_preauth_line_medical_category
    ON pre_authorization_lines(medical_category_id) WHERE medical_category_id IS NOT NULL;

-- The line snapshot proves what the line was understood to BE, so a later
-- conversion can show the claim is for the same thing under the same rule.
ALTER TABLE preauth_line_snapshots
    ADD COLUMN IF NOT EXISTS medical_service_id  BIGINT,
    ADD COLUMN IF NOT EXISTS medical_category_id BIGINT,
    ADD COLUMN IF NOT EXISTS benefit_rule_id     BIGINT
        REFERENCES benefit_policy_rules(id) ON DELETE RESTRICT;
