-- ============================================================
-- V18: دفعات المطالبات
-- ============================================================

CREATE TABLE IF NOT EXISTS claim_batches (
    id            BIGSERIAL PRIMARY KEY,
    batch_code    VARCHAR(30) NOT NULL UNIQUE,
    provider_id   BIGINT NOT NULL,
    employer_id   BIGINT NOT NULL,
    batch_year    INT NOT NULL,
    batch_month   INT NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at     TIMESTAMP NULL,

    CONSTRAINT uk_claim_batch_provider_period UNIQUE (provider_id, employer_id, batch_year, batch_month)
);

CREATE INDEX IF NOT EXISTS idx_claim_batch_lookup
    ON claim_batches(provider_id, employer_id, batch_year, batch_month);
