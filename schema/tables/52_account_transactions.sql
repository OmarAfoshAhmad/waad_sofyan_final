-- ============================================================
-- Table: account_transactions
-- Depends on: provider_accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS account_transactions (
    id                   BIGSERIAL PRIMARY KEY,
    provider_account_id  BIGINT NOT NULL,

    transaction_type     VARCHAR(20) NOT NULL
        CHECK (transaction_type IN ('CREDIT','DEBIT')),
    amount               NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    balance_before       NUMERIC(15,2) NOT NULL,
    balance_after        NUMERIC(15,2) NOT NULL,

    reference_type       VARCHAR(50) NOT NULL
        CHECK (reference_type IN ('CLAIM_APPROVAL','SETTLEMENT_PAYMENT','ADJUSTMENT')),
    reference_id         BIGINT,
    reference_number     VARCHAR(100),

    description          VARCHAR(500),
    transaction_date     DATE NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by           BIGINT,

    CONSTRAINT fk_transaction_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_balance_credit CHECK (
        transaction_type <> 'CREDIT' OR balance_after = balance_before + amount
    ),
    CONSTRAINT chk_balance_debit CHECK (
        transaction_type <> 'DEBIT' OR balance_after = balance_before - amount
    ),
    CONSTRAINT chk_transaction_balance_non_negative CHECK (balance_before >= 0 AND balance_after >= 0)
);

CREATE INDEX IF NOT EXISTS idx_transactions_account          ON account_transactions(provider_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type             ON account_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_reference        ON account_transactions(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date             ON account_transactions(transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_created          ON account_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_provider_date    ON account_transactions(provider_account_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_reporting        ON account_transactions(transaction_date, transaction_type, amount);
CREATE INDEX IF NOT EXISTS idx_account_transactions_provider_date ON account_transactions(provider_account_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_reporting_full   ON account_transactions(transaction_date, transaction_type, amount);
