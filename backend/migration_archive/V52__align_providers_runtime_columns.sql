-- Align providers table with Provider entity fields

ALTER TABLE providers
    ADD COLUMN IF NOT EXISTS name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS contract_start_date DATE,
    ADD COLUMN IF NOT EXISTS contract_end_date DATE,
    ADD COLUMN IF NOT EXISTS default_discount_rate NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS network_status VARCHAR(20);

UPDATE providers
SET name = COALESCE(name, provider_name)
WHERE name IS NULL;

ALTER TABLE providers
    ALTER COLUMN name SET NOT NULL;
