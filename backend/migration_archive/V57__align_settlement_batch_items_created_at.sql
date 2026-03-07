-- Minimal alignment for SettlementBatchItem.createdAt
-- Entity expects: created_at TIMESTAMP NOT NULL

ALTER TABLE settlement_batch_items
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE settlement_batch_items
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE settlement_batch_items
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET NOT NULL;
