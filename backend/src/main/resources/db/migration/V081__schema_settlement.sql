-- ============================================================
-- V081: Settlement batches, batch items, and provider payments
-- ============================================================
-- Depends on: V006 (providers), V070 (claims), V080 (provider_accounts),
--             V010 (users)

-- ----------------------------------------------------------
-- SECTION 1: Settlement batches (monthly/periodic batch groups)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS settlement_batches (
    id              BIGINT PRIMARY KEY DEFAULT nextval('settlement_batch_seq'),
    batch_number    VARCHAR(100) NOT NULL UNIQUE,
    provider_id     BIGINT NOT NULL,

    -- Batch totals
    total_claims    INTEGER DEFAULT 0,
    total_amount    NUMERIC(14,2) NOT NULL CHECK (total_amount > 0),

    -- Status machine (DRAFT → CONFIRMED → PAID)
    status VARCHAR(50)
        CHECK (status IN ('DRAFT','CONFIRMED','PAID')),

    -- Optimistic locking
    version BIGINT DEFAULT 0,

    -- Audit
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirmed_at  TIMESTAMP,
    paid_at       TIMESTAMP,
    created_by    VARCHAR(255),
    confirmed_by  VARCHAR(255),
    paid_by       VARCHAR(255),

    CONSTRAINT fk_settlement_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_batches_provider  ON settlement_batches(provider_id);
CREATE INDEX IF NOT EXISTS idx_batches_status    ON settlement_batches(status);
CREATE INDEX IF NOT EXISTS idx_batches_pending   ON settlement_batches(provider_id, status)
    WHERE status = 'DRAFT';
CREATE INDEX IF NOT EXISTS idx_batches_provider_date ON settlement_batches(provider_id, created_at DESC, status);
CREATE INDEX IF NOT EXISTS idx_batches_payment_summary ON settlement_batches(status, paid_at, total_amount);

-- ----------------------------------------------------------
-- SECTION 2: Batch items (claims attached to a batch)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS settlement_batch_items (
    id          BIGSERIAL PRIMARY KEY,
    batch_id    BIGINT NOT NULL,
    claim_id    BIGINT NOT NULL,
    claim_amount NUMERIC(12,2) NOT NULL,
    added_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by    VARCHAR(255),

    CONSTRAINT fk_batch_item_batch FOREIGN KEY (batch_id)  REFERENCES settlement_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_item_claim FOREIGN KEY (claim_id)  REFERENCES claims(id)             ON DELETE RESTRICT,
    CONSTRAINT uq_batch_item_claim UNIQUE (claim_id)  -- one claim per batch, prevents double-settlement
);

CREATE INDEX IF NOT EXISTS idx_batch_items_batch ON settlement_batch_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_batch_items_claim ON settlement_batch_items(claim_id);

-- ----------------------------------------------------------
-- SECTION 3: Provider payment records (one payment per batch)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_payments (
    id                  BIGSERIAL PRIMARY KEY,
    settlement_batch_id BIGINT NOT NULL,
    provider_id         BIGINT NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    payment_reference   VARCHAR(100) NOT NULL,
    payment_method      VARCHAR(50),
    payment_date        TIMESTAMP NOT NULL,
    notes               TEXT,
    created_by          BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payments_reference  UNIQUE (payment_reference),
    CONSTRAINT uq_payments_batch      UNIQUE (settlement_batch_id),
    CONSTRAINT fk_payment_batch       FOREIGN KEY (settlement_batch_id) REFERENCES settlement_batches(id),
    CONSTRAINT fk_payment_provider    FOREIGN KEY (provider_id)         REFERENCES providers(id),
    CONSTRAINT fk_payment_created_by  FOREIGN KEY (created_by)          REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_provider_payments_provider ON provider_payments(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_payments_date     ON provider_payments(payment_date DESC);
