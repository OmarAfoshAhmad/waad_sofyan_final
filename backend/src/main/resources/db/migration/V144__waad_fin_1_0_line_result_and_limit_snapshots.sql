-- ============================================================
-- WAAD-FIN-1.0: WaadFinancialEngine Result columns on claim_lines,
-- and the append-only claim_line_limit_snapshots interpretive record.
--
-- Not yet populated by production code: ClaimMapper still calls the old
-- ClaimLineFinancialEngine (see backend/docs/design/FINANCIAL_CONSTITUTION.md
-- and the finance-constitution branch history). This migration only shapes
-- the target schema so the "unified limit semantics" wiring phase has
-- somewhere correct to write. Existing columns (company_share, patient_share,
-- refused_amount, ...) are NOT touched or removed -- the old engine still
-- reads/writes them until the switchover.
-- ============================================================

-- ── claim_lines: WaadFinancialEngine.Result, field-for-field ───────────────

ALTER TABLE claim_lines
    ADD COLUMN contractual_price             NUMERIC(15,2) CHECK (contractual_price IS NULL OR contractual_price >= 0),
    ADD COLUMN contractual_price_excess       NUMERIC(15,2) CHECK (contractual_price_excess IS NULL OR contractual_price_excess >= 0),
    ADD COLUMN settlement_base                NUMERIC(15,2) CHECK (settlement_base IS NULL OR settlement_base >= 0),

    ADD COLUMN limit_mode                     VARCHAR(20)
        CHECK (limit_mode IS NULL OR limit_mode IN ('UNLIMITED', 'LIMITED')),
    ADD COLUMN binding_available_limit        NUMERIC(15,2) CHECK (binding_available_limit IS NULL OR binding_available_limit >= 0),
    ADD COLUMN inside_limit                   NUMERIC(15,2) CHECK (inside_limit IS NULL OR inside_limit >= 0),
    ADD COLUMN patient_limit_excess           NUMERIC(15,2) CHECK (patient_limit_excess IS NULL OR patient_limit_excess >= 0),
    ADD COLUMN limit_consumption              NUMERIC(15,2) CHECK (limit_consumption IS NULL OR limit_consumption >= 0),
    ADD COLUMN binding_remaining_limit        NUMERIC(15,2) CHECK (binding_remaining_limit IS NULL OR binding_remaining_limit >= 0),

    ADD COLUMN patient_coverage_share         NUMERIC(15,2) CHECK (patient_coverage_share IS NULL OR patient_coverage_share >= 0),
    ADD COLUMN patient_total_responsibility   NUMERIC(15,2) CHECK (patient_total_responsibility IS NULL OR patient_total_responsibility >= 0),

    ADD COLUMN insurer_gross_share            NUMERIC(15,2) CHECK (insurer_gross_share IS NULL OR insurer_gross_share >= 0),
    ADD COLUMN provider_discount_percent      NUMERIC(5,2)
        CHECK (provider_discount_percent IS NULL OR (provider_discount_percent >= 0 AND provider_discount_percent <= 100)),
    ADD COLUMN provider_contract_discount     NUMERIC(15,2) CHECK (provider_contract_discount IS NULL OR provider_contract_discount >= 0),
    ADD COLUMN provider_net_before_rejection  NUMERIC(15,2) CHECK (provider_net_before_rejection IS NULL OR provider_net_before_rejection >= 0),
    ADD COLUMN provider_rejected_amount_v2    NUMERIC(15,2) CHECK (provider_rejected_amount_v2 IS NULL OR provider_rejected_amount_v2 >= 0),
    ADD COLUMN insurer_final_payment          NUMERIC(15,2) CHECK (insurer_final_payment IS NULL OR insurer_final_payment >= 0);

COMMENT ON COLUMN claim_lines.provider_rejected_amount_v2 IS
    'WAAD-FIN-1.0 canonical rejection amount (WaadFinancialEngine.Result.providerRejectedAmount). '
    'Named _v2 to avoid colliding with the pre-existing manual_refused_amount column, which the old '
    'engine still owns until the switchover; the suffix is dropped in the migration that retires the '
    'old engine and its columns together.';

COMMENT ON COLUMN claim_lines.limit_mode IS
    'WaadFinancialEngine.LimitMode. LIMITED rows populate binding_available_limit/inside_limit/'
    'limit_consumption/binding_remaining_limit; UNLIMITED rows leave those four NULL ("not applicable" '
    '-- never zero, which would mean an exhausted limit instead of no limit at all).';

-- ── claim_line_limit_snapshots: one row per (ClaimLine, applicable limit, calculationVersion) ──
--
-- Interpretive, historical record -- answers "what limits applied, what were
-- their balances, which one governed, and how did the line's result follow
-- from that?" This is NOT the operational consumption ledger
-- (benefit_bucket_consumptions already serves that purpose, unchanged by
-- this migration): a snapshot row here records the state of ALL applicable
-- limits for a line, including the ones that were NOT binding, so the claim
-- remains fully explainable later. It is written once, at the final
-- approval gate, inside the same transaction as the line's financial result,
-- the bucket ledger entries, and the status change to APPROVED -- never at
-- draft time, and never edited afterward (see the append-only trigger below).
CREATE TABLE claim_line_limit_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    claim_id                    BIGINT NOT NULL REFERENCES claims(id) ON DELETE RESTRICT,
    claim_line_id               BIGINT NOT NULL REFERENCES claim_lines(id) ON DELETE RESTRICT,
    calculation_version         INTEGER NOT NULL,

    limit_scope_type            VARCHAR(20) NOT NULL,
    limit_semantic_key          VARCHAR(200) NOT NULL,
    bucket_id                   BIGINT REFERENCES benefit_limit_buckets(id) ON DELETE RESTRICT,

    policy_id                   BIGINT NOT NULL REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    benefit_rule_id             BIGINT REFERENCES benefit_policy_rules(id) ON DELETE RESTRICT,
    benefit_group_id            BIGINT REFERENCES benefit_groups(id) ON DELETE RESTRICT,

    source_type                 VARCHAR(30) NOT NULL,
    source_id                   BIGINT,
    source_version              BIGINT,
    structure_revision          INTEGER,

    -- Denormalized copy of the governing bucket's period_type at the time of
    -- this snapshot -- deliberately NOT constrained to benefit_limit_buckets'
    -- own CHECK list here, so this table never has to be migrated in lockstep
    -- if that list changes; it is a historical record of what applied, not a
    -- live foreign reference to the enum.
    period_type                 VARCHAR(20) NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE,

    effective_limit             NUMERIC(15,2) NOT NULL CHECK (effective_limit >= 0),
    consumed_before              NUMERIC(15,2) NOT NULL CHECK (consumed_before >= 0),
    reserved_before              NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (reserved_before >= 0),
    binding_available_before    NUMERIC(15,2) NOT NULL CHECK (binding_available_before >= 0),

    line_settlement_base        NUMERIC(15,2) NOT NULL CHECK (line_settlement_base >= 0),
    line_inside_limit           NUMERIC(15,2) NOT NULL CHECK (line_inside_limit >= 0),
    limit_consumption           NUMERIC(15,2) NOT NULL CHECK (limit_consumption >= 0),
    patient_limit_excess        NUMERIC(15,2) NOT NULL CHECK (patient_limit_excess >= 0),

    available_after              NUMERIC(15,2) NOT NULL CHECK (available_after >= 0),
    is_binding                  BOOLEAN NOT NULL DEFAULT false,

    consumption_order           INTEGER NOT NULL DEFAULT 1,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_limit_snapshot_line_key_version
        UNIQUE (claim_line_id, limit_semantic_key, calculation_version),

    CONSTRAINT chk_limit_snapshot_scope_type CHECK (limit_scope_type IN
        ('SERVICE', 'CATEGORY', 'GROUP', 'POLICY_GENERAL', 'FAMILY', 'LIFETIME')),

    CONSTRAINT chk_limit_snapshot_source_type CHECK (source_type IN
        ('POLICY_DEFAULT', 'EMPLOYER_OVERRIDE', 'MEMBER_OVERRIDE', 'PREAUTH_RESERVATION')),

    -- The general annual ceiling is not always a real benefit_limit_buckets
    -- row (S15/S4 of the constitution): never invent a fake bucket just to
    -- satisfy a NOT NULL foreign key.
    CONSTRAINT chk_limit_snapshot_policy_general_bucket CHECK (
        (limit_scope_type = 'POLICY_GENERAL' AND bucket_id IS NULL)
        OR (limit_scope_type <> 'POLICY_GENERAL' AND bucket_id IS NOT NULL)
    ),

    -- Same-row arithmetic identity: this is what actually happened to this
    -- specific limit's balance, expressed as a database-enforced fact, not a
    -- convention.
    CONSTRAINT chk_limit_snapshot_available_after CHECK (
        available_after = GREATEST(0, binding_available_before - limit_consumption)
    )
);

CREATE INDEX idx_limit_snapshot_claim ON claim_line_limit_snapshots(claim_id);
CREATE INDEX idx_limit_snapshot_line_version ON claim_line_limit_snapshots(claim_line_id, calculation_version);
CREATE INDEX idx_limit_snapshot_bucket ON claim_line_limit_snapshots(bucket_id) WHERE bucket_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_limit_snapshot_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'claim_line_limit_snapshots is append-only; % is not allowed on row %',
        TG_OP, COALESCE(OLD.id, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_limit_snapshot_no_update
    BEFORE UPDATE ON claim_line_limit_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_limit_snapshot_mutation();

CREATE TRIGGER trg_limit_snapshot_no_delete
    BEFORE DELETE ON claim_line_limit_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_limit_snapshot_mutation();
