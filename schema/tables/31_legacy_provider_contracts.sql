-- ============================================================
-- Table: legacy_provider_contracts
-- Depends on: providers
-- ============================================================
CREATE TABLE IF NOT EXISTS legacy_provider_contracts (
    id             BIGSERIAL PRIMARY KEY,
    provider_id    BIGINT NOT NULL,
    service_code   VARCHAR(50)   NOT NULL,
    contract_price NUMERIC(10,2) NOT NULL,
    currency       VARCHAR(3)    DEFAULT 'LYD',
    effective_from DATE NOT NULL,
    effective_to   DATE,
    active         BOOLEAN DEFAULT true,
    notes          VARCHAR(500),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_legacy_contract_service_date
    ON legacy_provider_contracts(provider_id, service_code, effective_from);
CREATE INDEX IF NOT EXISTS idx_legacy_contracts_provider ON legacy_provider_contracts(provider_id);
CREATE INDEX IF NOT EXISTS idx_legacy_contracts_service  ON legacy_provider_contracts(service_code);
CREATE INDEX IF NOT EXISTS idx_legacy_contracts_active   ON legacy_provider_contracts(active);
