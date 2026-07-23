-- V92: Employer-scoped provider price lists.
-- Existing experimental data is treated as GLOBAL provider pricing.

ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS pricing_scope VARCHAR(30) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS employer_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'provider_contracts'
          AND constraint_name = 'fk_provider_contracts_employer'
    ) THEN
        ALTER TABLE provider_contracts
            ADD CONSTRAINT fk_provider_contracts_employer
            FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE RESTRICT;
    END IF;
END $$;

UPDATE provider_contracts
SET pricing_scope = 'GLOBAL'
WHERE pricing_scope IS NULL;

UPDATE provider_contracts
SET employer_id = NULL
WHERE pricing_scope = 'GLOBAL';

ALTER TABLE provider_contracts
    DROP CONSTRAINT IF EXISTS chk_provider_contract_pricing_scope;

ALTER TABLE provider_contracts
    ADD CONSTRAINT chk_provider_contract_pricing_scope
    CHECK (pricing_scope IN ('GLOBAL', 'EMPLOYER_SPECIFIC'));

ALTER TABLE provider_contracts
    DROP CONSTRAINT IF EXISTS chk_provider_contract_scope_employer;

ALTER TABLE provider_contracts
    ADD CONSTRAINT chk_provider_contract_scope_employer
    CHECK (
        (pricing_scope = 'GLOBAL' AND employer_id IS NULL)
        OR
        (pricing_scope = 'EMPLOYER_SPECIFIC' AND employer_id IS NOT NULL)
    );

DROP INDEX IF EXISTS uq_active_contract_per_provider;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_global_contract_per_provider
    ON provider_contracts(provider_id)
    WHERE active = true AND status = 'ACTIVE' AND pricing_scope = 'GLOBAL';

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_employer_contract_per_provider
    ON provider_contracts(provider_id, employer_id)
    WHERE active = true AND status = 'ACTIVE' AND pricing_scope = 'EMPLOYER_SPECIFIC';

CREATE INDEX IF NOT EXISTS idx_provider_contracts_scope_employer
    ON provider_contracts(provider_id, pricing_scope, employer_id);

CREATE INDEX IF NOT EXISTS idx_provider_contracts_employer
    ON provider_contracts(employer_id);
