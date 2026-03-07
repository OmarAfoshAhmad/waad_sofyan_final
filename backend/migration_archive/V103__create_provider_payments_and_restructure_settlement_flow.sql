-- Restructure settlement payment model:
-- - Introduce provider_payments as single source of payment records
-- - Remove payment details from settlement_batches
-- - Restrict settlement batch statuses to DRAFT, CONFIRMED, PAID

CREATE TABLE IF NOT EXISTS provider_payments (
    id BIGSERIAL PRIMARY KEY,
    settlement_batch_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    payment_reference VARCHAR(100) NOT NULL,
    payment_method VARCHAR(50),
    payment_date TIMESTAMP NOT NULL,
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_payments_reference UNIQUE (payment_reference),
    CONSTRAINT uq_provider_payments_batch UNIQUE (settlement_batch_id),
    CONSTRAINT fk_provider_payments_batch FOREIGN KEY (settlement_batch_id) REFERENCES settlement_batches(id),
    CONSTRAINT fk_provider_payments_provider FOREIGN KEY (provider_id) REFERENCES providers(id),
    CONSTRAINT fk_provider_payments_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_provider_payments_provider_id ON provider_payments(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_payments_payment_date ON provider_payments(payment_date);

ALTER TABLE settlement_batches DROP COLUMN IF EXISTS payment_reference;
ALTER TABLE settlement_batches DROP COLUMN IF EXISTS payment_method;
ALTER TABLE settlement_batches DROP COLUMN IF EXISTS payment_date;
ALTER TABLE settlement_batches DROP COLUMN IF EXISTS bank_account_number;

UPDATE settlement_batches
SET status = 'DRAFT'
WHERE status = 'CANCELLED';

ALTER TABLE settlement_batches DROP CONSTRAINT IF EXISTS settlement_batches_status_check;
ALTER TABLE settlement_batches
    ADD CONSTRAINT settlement_batches_status_check
    CHECK (status IN ('DRAFT', 'CONFIRMED', 'PAID'));
