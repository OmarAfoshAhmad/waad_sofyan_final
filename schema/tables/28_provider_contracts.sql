-- ============================================================
-- Table: provider_contracts
-- Depends on: providers, employers
-- ============================================================
CREATE TABLE IF NOT EXISTS provider_contracts (
    id                        BIGINT PRIMARY KEY DEFAULT nextval('provider_contract_seq'),
    provider_id               BIGINT NOT NULL,
    employer_id               BIGINT NOT NULL,
    contract_number           VARCHAR(100) NOT NULL UNIQUE,

    contract_start_date       DATE NOT NULL,
    contract_end_date         DATE,
    discount_percent          NUMERIC(5,2),
    payment_terms             VARCHAR(100),

    max_sessions_per_service  INTEGER,
    requires_preauthorization BOOLEAN DEFAULT false,

    contract_status           VARCHAR(50)
        CHECK (contract_status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
    active                    BOOLEAN DEFAULT true,
    status                    VARCHAR(20) DEFAULT 'DRAFT',

    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),

    CONSTRAINT fk_contract_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_contract_dates   CHECK (contract_end_date IS NULL OR contract_end_date >= contract_start_date)
);

CREATE INDEX IF NOT EXISTS idx_contracts_provider ON provider_contracts(provider_id);
CREATE INDEX IF NOT EXISTS idx_contracts_employer ON provider_contracts(employer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_status   ON provider_contracts(contract_status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_contract_per_provider ON provider_contracts(provider_id)
    WHERE contract_status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_provider_contracts_active   ON provider_contracts(active);
CREATE INDEX IF NOT EXISTS idx_provider_contracts_expiring ON provider_contracts(contract_end_date)
    WHERE active = true AND contract_end_date IS NOT NULL;
