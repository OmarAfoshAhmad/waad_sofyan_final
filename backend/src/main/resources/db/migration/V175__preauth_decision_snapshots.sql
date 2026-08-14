-- ============================================================
-- V175: the pre-authorization decision snapshot.
--
-- A pre-authorization approved in March may be converted to a claim in
-- September. Between those dates the member's policy assignment can change,
-- the policy's structure can be revised, the provider's contract terms can be
-- renegotiated, and the bucket balances certainly move. If conversion re-reads
-- "the current policy" and "the current contract", the money the provider was
-- promised is not the money they are paid, and nothing in the record explains
-- why.
--
-- These three tables are that explanation. They record what was decided, on
-- what basis, against which balances -- once, at the approval gate, and never
-- again. Conversion reads THEM, not the live configuration.
--
-- Three levels, because the decision has three:
--
--   preauth_decision_snapshots     one per approval  -- the basis
--   preauth_line_snapshots         one per line      -- the money
--   preauth_line_limit_snapshots   one per (line, limit) -- the balances
--
-- The third is separate for the same reason claim_line_limit_snapshots is: a
-- line can consume several buckets at once plus the general ceiling, and
-- collapsing them into the line row would make it impossible to answer "which
-- limit actually bound this decision, and what were the others?". It also
-- keeps a line's several scopes from being summed into one figure, which is
-- the double-counting error the ledger's own scope split exists to prevent.
--
-- Append-only, like every financial record in this system: a corrected
-- decision writes a new calculation_version, it does not edit the old one.
-- Tables only in this migration. No writer exists yet -- the approval service
-- is the next step and is the first component allowed to write here.
-- ============================================================

-- ── 1. The basis of the decision ─────────────────────────────────────────
CREATE TABLE preauth_decision_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    preauth_id                  BIGINT NOT NULL REFERENCES pre_authorizations(id) ON DELETE RESTRICT,
    calculation_version         INTEGER NOT NULL,

    member_id                   BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,

    -- WHICH coverage period, not merely which policy. Two separate assignments
    -- can point at the same logical policy, and only the assignment
    -- distinguishes them (V171/V172).
    member_policy_assignment_id BIGINT REFERENCES member_policy_assignments(id) ON DELETE RESTRICT,
    policy_id                   BIGINT NOT NULL REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    structure_revision          INTEGER,

    -- The date the decision was made FOR. Every temporal resolution here --
    -- policy, assignment, contract terms -- must have used this date, never
    -- "today". A pre-authorization is by definition about a future service.
    expected_service_date       DATE NOT NULL,

    provider_id                 BIGINT NOT NULL REFERENCES providers(id) ON DELETE RESTRICT,
    provider_contract_id        BIGINT REFERENCES provider_contracts(id) ON DELETE RESTRICT,
    -- The exact terms row, plus the two values that decide money, copied. The
    -- FK says which row; the copies survive even that row being superseded.
    contract_terms_id           BIGINT REFERENCES provider_contract_terms(id) ON DELETE RESTRICT,
    discount_percent            NUMERIC(5,2) CHECK (discount_percent IS NULL
                                    OR discount_percent BETWEEN 0 AND 100),
    -- Whether the discount applied before or after rejected amounts were
    -- removed. The same percentage yields different money either way, so the
    -- ORDER is part of the decision, not a detail of the contract.
    discount_before_rejection   BOOLEAN,

    -- Totals, each recorded rather than derived, so a later change in how
    -- totals are computed cannot silently restate a past decision.
    requested_total             NUMERIC(15,2) NOT NULL CHECK (requested_total >= 0),
    approved_total              NUMERIC(15,2) NOT NULL CHECK (approved_total >= 0),
    rejected_total              NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (rejected_total >= 0),
    patient_share_total         NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (patient_share_total >= 0),
    company_share_total         NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (company_share_total >= 0),

    decision_status             VARCHAR(25) NOT NULL,

    decided_by                  VARCHAR(150) NOT NULL,
    decided_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Makes re-running an approval a no-op instead of a second set of holds.
    idempotency_key             VARCHAR(200) NOT NULL UNIQUE,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One snapshot per approval attempt. A correction is a new version.
    CONSTRAINT uq_preauth_snapshot_version UNIQUE (preauth_id, calculation_version),

    -- Only a decision that GRANTS something may hold a member's limit, so only
    -- these statuses may produce a snapshot at all.
    CONSTRAINT chk_preauth_snapshot_status CHECK (
        decision_status IN ('APPROVED', 'PARTIALLY_APPROVED')),

    -- The shares must account for the approved amount exactly. Stated as a
    -- database fact because "the two shares add up" is the property every
    -- downstream settlement figure assumes.
    CONSTRAINT chk_preauth_snapshot_shares CHECK (
        patient_share_total + company_share_total = approved_total),

    -- A partial approval approves less than was asked; a full one approves all
    -- of it. Either way nothing may be approved beyond what was requested.
    CONSTRAINT chk_preauth_snapshot_totals CHECK (approved_total <= requested_total),
    CONSTRAINT chk_preauth_snapshot_partial CHECK (
        (decision_status = 'PARTIALLY_APPROVED' AND approved_total < requested_total)
        OR
        (decision_status = 'APPROVED' AND approved_total = requested_total)
    ),

    -- Contract terms are optional (a non-contracted provider has none), but a
    -- terms row without its contract, or a discount without the terms that set
    -- it, describes a basis that cannot be reconstructed.
    CONSTRAINT chk_preauth_snapshot_contract_shape CHECK (
        (contract_terms_id IS NULL AND discount_percent IS NULL
            AND discount_before_rejection IS NULL)
        OR
        (contract_terms_id IS NOT NULL AND provider_contract_id IS NOT NULL
            AND discount_percent IS NOT NULL AND discount_before_rejection IS NOT NULL)
    )
);

CREATE INDEX idx_preauth_snapshot_preauth ON preauth_decision_snapshots(preauth_id);
CREATE INDEX idx_preauth_snapshot_member ON preauth_decision_snapshots(member_id);

-- ── 2. The money, per line ───────────────────────────────────────────────
CREATE TABLE preauth_line_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    decision_snapshot_id        BIGINT NOT NULL
                                    REFERENCES preauth_decision_snapshots(id) ON DELETE RESTRICT,
    preauth_line_id             BIGINT NOT NULL
                                    REFERENCES pre_authorization_lines(id) ON DELETE RESTRICT,

    -- Identity of what was approved, copied rather than referenced: conversion
    -- must be able to prove the claim is for the SAME service, even if the
    -- service catalogue is edited in between.
    provider_service_id         BIGINT,
    service_code                VARCHAR(50),
    service_name                VARCHAR(500),

    quantity                    INTEGER NOT NULL CHECK (quantity > 0),
    unit_price                  NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
    requested_amount            NUMERIC(15,2) NOT NULL CHECK (requested_amount >= 0),

    coverage_percent            INTEGER CHECK (coverage_percent IS NULL
                                    OR coverage_percent BETWEEN 0 AND 100),
    copay_amount                NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (copay_amount >= 0),
    rejected_amount             NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (rejected_amount >= 0),

    approved_amount             NUMERIC(15,2) NOT NULL CHECK (approved_amount >= 0),
    patient_share               NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (patient_share >= 0),
    company_share               NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (company_share >= 0),

    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_preauth_line_snapshot UNIQUE (decision_snapshot_id, preauth_line_id),

    CONSTRAINT chk_preauth_line_snapshot_shares CHECK (
        patient_share + company_share = approved_amount),

    -- Nothing may be approved beyond what was asked, and what was rejected
    -- plus what was approved cannot exceed the request either.
    CONSTRAINT chk_preauth_line_snapshot_approved CHECK (
        approved_amount + rejected_amount <= requested_amount)
);

CREATE INDEX idx_preauth_line_snapshot_decision ON preauth_line_snapshots(decision_snapshot_id);
CREATE INDEX idx_preauth_line_snapshot_line ON preauth_line_snapshots(preauth_line_id);

-- ── 3. The balances, per line and per limit ──────────────────────────────
-- One row per (line, limit) -- including limits that did NOT bind, so the
-- decision stays explainable: "why did this stop at 500, and where did the
-- other applicable limits stand?"
CREATE TABLE preauth_line_limit_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    line_snapshot_id            BIGINT NOT NULL
                                    REFERENCES preauth_line_snapshots(id) ON DELETE RESTRICT,

    -- Same two-part identity the ledger uses, so a snapshot row and a
    -- consumption row can be matched without guessing.
    limit_scope                 VARCHAR(20) NOT NULL,
    limit_semantic_key          VARCHAR(200) NOT NULL,
    bucket_id                   BIGINT REFERENCES benefit_limit_buckets(id) ON DELETE RESTRICT,
    policy_id                   BIGINT NOT NULL REFERENCES benefit_policies(id) ON DELETE RESTRICT,

    period_type                 VARCHAR(20) NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE,

    effective_limit             NUMERIC(15,2) NOT NULL CHECK (effective_limit >= 0),
    committed_before            NUMERIC(15,2) NOT NULL CHECK (committed_before >= 0),
    reserved_before             NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (reserved_before >= 0),

    -- The two figures kept apart everywhere else in this system, kept apart
    -- here too. actual_remaining is what the member has actually got left;
    -- reservable_available is what a NEW decision may consume, which is
    -- smaller because existing holds have already spoken for part of it.
    -- Recording only one of them would make the decision unreproducible.
    actual_remaining_before     NUMERIC(15,2) NOT NULL,
    reservable_available_before NUMERIC(15,2) NOT NULL,

    -- What this decision held against THIS limit. Zero is meaningful: it
    -- records a limit that applied and was not consumed.
    reserved_amount             NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (reserved_amount >= 0),
    is_binding                  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_preauth_limit_snapshot UNIQUE (line_snapshot_id, limit_semantic_key),

    CONSTRAINT chk_preauth_limit_snapshot_scope CHECK (limit_scope IN ('BUCKET', 'POLICY_GENERAL')),

    -- Mirrors the ledger exactly: the general ceiling is synthetic and has no
    -- bucket row, and a bucket movement must name its bucket.
    CONSTRAINT chk_preauth_limit_snapshot_bucket CHECK (
        (limit_scope = 'BUCKET' AND bucket_id IS NOT NULL)
        OR
        (limit_scope = 'POLICY_GENERAL' AND bucket_id IS NULL)
    ),

    -- The arithmetic that defines the two figures, enforced rather than
    -- assumed. Both are floored at zero: an over-consumed limit is a real
    -- state, and a negative "remaining" would corrupt every sum built on it.
    CONSTRAINT chk_preauth_limit_snapshot_actual_remaining CHECK (
        actual_remaining_before = GREATEST(0, effective_limit - committed_before)),
    CONSTRAINT chk_preauth_limit_snapshot_reservable CHECK (
        reservable_available_before = GREATEST(0, actual_remaining_before - reserved_before)),

    -- A decision may never hold more than was available to hold. This is the
    -- overdraw rule, stated where it cannot be forgotten.
    CONSTRAINT chk_preauth_limit_snapshot_within_available CHECK (
        reserved_amount <= reservable_available_before)
);

CREATE INDEX idx_preauth_limit_snapshot_line ON preauth_line_limit_snapshots(line_snapshot_id);
CREATE INDEX idx_preauth_limit_snapshot_bucket
    ON preauth_line_limit_snapshots(bucket_id) WHERE bucket_id IS NOT NULL;

-- ── 4. Append-only ───────────────────────────────────────────────────────
-- A snapshot that can be edited is not a snapshot. Conversion trusts these
-- rows precisely because nothing between approval and conversion can have
-- altered them.
CREATE OR REPLACE FUNCTION prevent_preauth_snapshot_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed. '
        'A corrected decision is written as a new calculation_version.',
        TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_preauth_decision_snapshot_no_update
    BEFORE UPDATE ON preauth_decision_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_preauth_snapshot_mutation();
CREATE TRIGGER trg_preauth_decision_snapshot_no_delete
    BEFORE DELETE ON preauth_decision_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_preauth_snapshot_mutation();

CREATE TRIGGER trg_preauth_line_snapshot_no_update
    BEFORE UPDATE ON preauth_line_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_preauth_snapshot_mutation();
CREATE TRIGGER trg_preauth_line_snapshot_no_delete
    BEFORE DELETE ON preauth_line_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_preauth_snapshot_mutation();

CREATE TRIGGER trg_preauth_limit_snapshot_no_update
    BEFORE UPDATE ON preauth_line_limit_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_preauth_snapshot_mutation();
CREATE TRIGGER trg_preauth_limit_snapshot_no_delete
    BEFORE DELETE ON preauth_line_limit_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_preauth_snapshot_mutation();

-- A line snapshot must belong to the pre-authorization its decision snapshot
-- is about. Both foreign keys can be satisfied by rows from two different
-- pre-authorizations, and no CHECK can see across tables to notice.
CREATE OR REPLACE FUNCTION validate_preauth_line_snapshot_ownership() RETURNS TRIGGER AS $$
DECLARE
    decision_preauth BIGINT;
    line_preauth BIGINT;
BEGIN
    SELECT preauth_id INTO decision_preauth
    FROM preauth_decision_snapshots WHERE id = NEW.decision_snapshot_id;

    SELECT pre_authorization_id INTO line_preauth
    FROM pre_authorization_lines WHERE id = NEW.preauth_line_id;

    IF decision_preauth IS DISTINCT FROM line_preauth THEN
        RAISE EXCEPTION
            'Pre-authorization line % belongs to pre-authorization %, but this snapshot describes %',
            NEW.preauth_line_id, line_preauth, decision_preauth;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_preauth_line_snapshot_ownership
    BEFORE INSERT ON preauth_line_snapshots
    FOR EACH ROW EXECUTE FUNCTION validate_preauth_line_snapshot_ownership();
