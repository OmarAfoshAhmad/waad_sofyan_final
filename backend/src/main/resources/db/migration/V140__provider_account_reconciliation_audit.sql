-- Reconciliation corrections (ProviderAccountAdjustmentService) must never be
-- representable as an ordinary account_transactions row: a row there would be
-- swept into any query that sums money movement for a provider, including the
-- very ledgerNet computation the correction was measured against — closing a
-- drift by writing a financial entry into the ledger it was compared to would
-- reintroduce the same drift on the next read. This table is the append-only,
-- explicitly non-financial home for what a correction actually is: a record of
-- why ProviderAccount.totalPaid was realigned, by whom, and to what.
CREATE TABLE provider_account_reconciliation_audits (
    id                              BIGSERIAL PRIMARY KEY,
    provider_account_id            BIGINT NOT NULL REFERENCES provider_accounts(id),
    provider_id                    BIGINT NOT NULL,
    adjustment_amount              NUMERIC(15,2) NOT NULL,
    total_paid_before              NUMERIC(15,2) NOT NULL,
    total_paid_after               NUMERIC(15,2) NOT NULL,
    running_balance_before         NUMERIC(15,2) NOT NULL,
    running_balance_after          NUMERIC(15,2) NOT NULL,
    ledger_vs_account_drift_before NUMERIC(15,2) NOT NULL,
    reason                         TEXT NOT NULL,
    performed_by                   VARCHAR(100) NOT NULL,
    performed_by_user_id           BIGINT,
    created_at                     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_reconciliation_audit_amount_nonzero CHECK (adjustment_amount <> 0),
    CONSTRAINT chk_reconciliation_audit_reason_present CHECK (length(trim(reason)) > 0)
);

CREATE INDEX idx_reconciliation_audit_provider ON provider_account_reconciliation_audits(provider_id);
CREATE INDEX idx_reconciliation_audit_account ON provider_account_reconciliation_audits(provider_account_id);

CREATE OR REPLACE FUNCTION prevent_reconciliation_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'provider_account_reconciliation_audits is append-only; % is not allowed on row %',
        TG_OP, COALESCE(OLD.id, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reconciliation_audit_no_update
    BEFORE UPDATE ON provider_account_reconciliation_audits
    FOR EACH ROW EXECUTE FUNCTION prevent_reconciliation_audit_mutation();

CREATE TRIGGER trg_reconciliation_audit_no_delete
    BEFORE DELETE ON provider_account_reconciliation_audits
    FOR EACH ROW EXECUTE FUNCTION prevent_reconciliation_audit_mutation();
