-- ============================================================
-- V080: Financial accounts and immutable transaction ledger
-- ============================================================
-- Depends on: V006 (providers), V010 (users)

-- ----------------------------------------------------------
-- SECTION 1: Provider financial accounts
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_accounts (
    id              BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT NOT NULL,
    account_type    VARCHAR(50),
    currency        VARCHAR(3) DEFAULT 'LYD',
    current_balance NUMERIC(14,2) DEFAULT 0.00,
    total_payable   NUMERIC(14,2) DEFAULT 0.00,
    version         BIGINT DEFAULT 0,   -- optimistic locking
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_account_provider   FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_balance_non_negative CHECK (current_balance >= 0)
);

-- ----------------------------------------------------------
-- SECTION 2: Account transactions (INSERT-ONLY immutable ledger)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS account_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    provider_account_id BIGINT NOT NULL,

    transaction_type VARCHAR(50)
        CHECK (transaction_type IN ('CREDIT','DEBIT')),
    amount          NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    balance_before  NUMERIC(14,2) NOT NULL,
    balance_after   NUMERIC(14,2) NOT NULL,

    -- Source document reference
    reference_type VARCHAR(50)
        CHECK (reference_type IN ('CLAIM_APPROVAL','SETTLEMENT_PAYMENT','ADJUSTMENT')),
    reference_id     BIGINT,
    reference_number VARCHAR(100),

    description      TEXT,
    transaction_date DATE NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,

    CONSTRAINT fk_transaction_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_balance_credit CHECK (
        transaction_type != 'CREDIT' OR balance_after = balance_before + amount
    ),
    CONSTRAINT chk_balance_debit CHECK (
        transaction_type != 'DEBIT' OR balance_after = balance_before - amount
    ),
    CONSTRAINT chk_balance_non_negative CHECK (balance_before >= 0 AND balance_after >= 0)
);

CREATE INDEX IF NOT EXISTS idx_transactions_account      ON account_transactions(provider_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type         ON account_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_reference    ON account_transactions(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date         ON account_transactions(transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_created      ON account_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_provider_date ON account_transactions(provider_account_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_reporting    ON account_transactions(transaction_date, transaction_type, amount);
