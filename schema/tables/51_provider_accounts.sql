-- ============================================================
-- Table: provider_accounts
-- Depends on: providers
-- ============================================================
CREATE TABLE IF NOT EXISTS provider_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    provider_id         BIGINT NOT NULL UNIQUE,
    running_balance     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_approved      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_paid          NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    last_transaction_at TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_account_provider      FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_balance_non_negative CHECK (running_balance >= 0)
);
