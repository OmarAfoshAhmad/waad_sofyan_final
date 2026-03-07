-- Align settlement_batch_items with SettlementBatchItem entity
-- Entity columns required:
--   id BIGINT PK
--   settlement_batch_id BIGINT NOT NULL
--   claim_id BIGINT NOT NULL UNIQUE
--   gross_amount_snapshot NUMERIC(15,2) NOT NULL
--   net_amount_snapshot NUMERIC(15,2) NOT NULL
--   patient_share_snapshot NUMERIC(15,2) NOT NULL
--   created_at TIMESTAMP NOT NULL

ALTER TABLE settlement_batch_items
    ADD COLUMN IF NOT EXISTS settlement_batch_id BIGINT,
    ADD COLUMN IF NOT EXISTS gross_amount_snapshot NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS net_amount_snapshot NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS patient_share_snapshot NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

UPDATE settlement_batch_items
SET settlement_batch_id = COALESCE(settlement_batch_id, batch_id)
WHERE settlement_batch_id IS NULL;

UPDATE settlement_batch_items
SET gross_amount_snapshot = COALESCE(gross_amount_snapshot, claim_amount::NUMERIC(15,2), 0.00)
WHERE gross_amount_snapshot IS NULL;

UPDATE settlement_batch_items
SET net_amount_snapshot = COALESCE(net_amount_snapshot, claim_amount::NUMERIC(15,2), 0.00)
WHERE net_amount_snapshot IS NULL;

UPDATE settlement_batch_items
SET patient_share_snapshot = COALESCE(patient_share_snapshot, 0.00)
WHERE patient_share_snapshot IS NULL;

UPDATE settlement_batch_items
SET created_at = COALESCE(created_at, added_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

ALTER TABLE settlement_batch_items
    ALTER COLUMN settlement_batch_id SET NOT NULL,
    ALTER COLUMN gross_amount_snapshot SET NOT NULL,
    ALTER COLUMN net_amount_snapshot SET NOT NULL,
    ALTER COLUMN patient_share_snapshot SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE settlement_batch_items
    ALTER COLUMN patient_share_snapshot SET DEFAULT 0.00;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_settlement_batch_items_batch'
          AND conrelid = 'settlement_batch_items'::regclass
    ) THEN
        ALTER TABLE settlement_batch_items
            ADD CONSTRAINT fk_settlement_batch_items_batch
            FOREIGN KEY (settlement_batch_id) REFERENCES settlement_batches(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_settlement_batch_items_batch
    ON settlement_batch_items(settlement_batch_id);

CREATE INDEX IF NOT EXISTS idx_settlement_batch_items_claim
    ON settlement_batch_items(claim_id);
