-- Align settlement_batches with SettlementBatch entity

ALTER TABLE settlement_batches
    ADD COLUMN IF NOT EXISTS provider_account_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_date DATE,
    ADD COLUMN IF NOT EXISTS total_claims_count INTEGER,
    ADD COLUMN IF NOT EXISTS total_gross_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS total_net_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS total_patient_share NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS cancelled_by BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

UPDATE settlement_batches
SET provider_account_id = COALESCE(provider_account_id, provider_id)
WHERE provider_account_id IS NULL;

UPDATE settlement_batches
SET settlement_date = COALESCE(settlement_date, payment_date, created_at::date, CURRENT_DATE)
WHERE settlement_date IS NULL;

UPDATE settlement_batches
SET total_claims_count = COALESCE(total_claims_count, total_claims, 0)
WHERE total_claims_count IS NULL;

UPDATE settlement_batches
SET total_gross_amount = COALESCE(total_gross_amount, total_amount::NUMERIC(15,2), 0.00)
WHERE total_gross_amount IS NULL;

UPDATE settlement_batches
SET total_net_amount = COALESCE(total_net_amount, total_amount::NUMERIC(15,2), 0.00)
WHERE total_net_amount IS NULL;

UPDATE settlement_batches
SET total_patient_share = COALESCE(total_patient_share, 0.00)
WHERE total_patient_share IS NULL;

ALTER TABLE settlement_batches
    ALTER COLUMN created_by TYPE BIGINT
    USING (CASE WHEN created_by IS NULL THEN NULL WHEN created_by ~ '^[0-9]+$' THEN created_by::BIGINT ELSE NULL END),
    ALTER COLUMN confirmed_by TYPE BIGINT
    USING (CASE WHEN confirmed_by IS NULL THEN NULL WHEN confirmed_by ~ '^[0-9]+$' THEN confirmed_by::BIGINT ELSE NULL END),
    ALTER COLUMN paid_by TYPE BIGINT
    USING (CASE WHEN paid_by IS NULL THEN NULL WHEN paid_by ~ '^[0-9]+$' THEN paid_by::BIGINT ELSE NULL END);

ALTER TABLE settlement_batches
    ALTER COLUMN total_claims_count SET DEFAULT 0,
    ALTER COLUMN total_gross_amount SET DEFAULT 0.00,
    ALTER COLUMN total_net_amount SET DEFAULT 0.00,
    ALTER COLUMN total_patient_share SET DEFAULT 0.00,
    ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE settlement_batches
    ALTER COLUMN provider_account_id SET NOT NULL,
    ALTER COLUMN settlement_date SET NOT NULL,
    ALTER COLUMN total_claims_count SET NOT NULL,
    ALTER COLUMN total_gross_amount SET NOT NULL,
    ALTER COLUMN total_net_amount SET NOT NULL,
    ALTER COLUMN total_patient_share SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_settlement_batches_provider_account'
          AND conrelid = 'settlement_batches'::regclass
    ) THEN
        ALTER TABLE settlement_batches
            ADD CONSTRAINT fk_settlement_batches_provider_account
            FOREIGN KEY (provider_account_id) REFERENCES provider_accounts(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_settlement_batches_provider_account
    ON settlement_batches(provider_account_id);

CREATE INDEX IF NOT EXISTS idx_settlement_batches_settlement_date
    ON settlement_batches(settlement_date);
