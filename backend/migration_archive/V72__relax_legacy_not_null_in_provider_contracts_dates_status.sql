-- V72: Relax legacy NOT NULL constraints still blocking modern ProviderContract inserts
-- Modern entity writes start_date/status, while legacy columns contract_start_date/contract_status are no longer populated.

ALTER TABLE provider_contracts
    ALTER COLUMN contract_start_date DROP NOT NULL,
    ALTER COLUMN contract_status DROP NOT NULL;
