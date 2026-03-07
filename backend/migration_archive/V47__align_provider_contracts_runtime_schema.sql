-- Align provider_contracts with ProviderContract entity mapping

ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS contract_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pricing_model VARCHAR(20),
    ADD COLUMN IF NOT EXISTS discount_rate NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS end_date DATE,
    ADD COLUMN IF NOT EXISTS signed_date DATE,
    ADD COLUMN IF NOT EXISTS total_value NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN,
    ADD COLUMN IF NOT EXISTS contact_person VARCHAR(100),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(100),
    ADD COLUMN IF NOT EXISTS notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

UPDATE provider_contracts
SET contract_code = COALESCE(contract_code, contract_number, ('CON-' || id::TEXT))
WHERE contract_code IS NULL;

UPDATE provider_contracts
SET status = COALESCE(status, contract_status)
WHERE status IS NULL;

UPDATE provider_contracts
SET pricing_model = COALESCE(pricing_model, 'DISCOUNT')
WHERE pricing_model IS NULL;

UPDATE provider_contracts
SET start_date = COALESCE(start_date, contract_start_date)
WHERE start_date IS NULL;

UPDATE provider_contracts
SET end_date = COALESCE(end_date, contract_end_date)
WHERE end_date IS NULL;

UPDATE provider_contracts
SET currency = COALESCE(currency, 'LYD')
WHERE currency IS NULL;

UPDATE provider_contracts
SET auto_renew = COALESCE(auto_renew, false)
WHERE auto_renew IS NULL;

UPDATE provider_contracts
SET active = COALESCE(active, true)
WHERE active IS NULL;

ALTER TABLE provider_contracts
    ALTER COLUMN contract_code SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN pricing_model SET NOT NULL,
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN auto_renew SET NOT NULL,
    ALTER COLUMN active SET NOT NULL;

ALTER TABLE provider_contracts
    ALTER COLUMN currency SET DEFAULT 'LYD',
    ALTER COLUMN auto_renew SET DEFAULT false,
    ALTER COLUMN active SET DEFAULT true;

CREATE UNIQUE INDEX IF NOT EXISTS uk_provider_contracts_contract_code
    ON provider_contracts(contract_code);

CREATE INDEX IF NOT EXISTS idx_contracts_provider_id
    ON provider_contracts(provider_id);

CREATE INDEX IF NOT EXISTS idx_contracts_status
    ON provider_contracts(status);

CREATE INDEX IF NOT EXISTS idx_contracts_contract_code
    ON provider_contracts(contract_code);

CREATE INDEX IF NOT EXISTS idx_contracts_start_date
    ON provider_contracts(start_date);

CREATE INDEX IF NOT EXISTS idx_contracts_end_date
    ON provider_contracts(end_date);
