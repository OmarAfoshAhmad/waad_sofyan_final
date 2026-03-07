-- ============================================================
-- V045: Provider contracts and pricing
-- ============================================================
-- Depends on: V006 (providers), V005 (employers), V021 (medical_services)

-- ----------------------------------------------------------
-- SECTION 1: Provider contracts
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_contracts (
    id              BIGINT PRIMARY KEY DEFAULT nextval('provider_contract_seq'),
    provider_id     BIGINT NOT NULL,
    employer_id     BIGINT NOT NULL,
    contract_number VARCHAR(100) NOT NULL UNIQUE,

    -- Contract terms
    contract_start_date DATE NOT NULL,
    contract_end_date   DATE,
    discount_percent    NUMERIC(5,2),
    payment_terms       VARCHAR(100),

    -- Business rules
    max_sessions_per_service    INTEGER,
    requires_preauthorization   BOOLEAN DEFAULT false,

    -- Status
    contract_status VARCHAR(50)
        CHECK (contract_status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
    active  BOOLEAN DEFAULT true,
    status  VARCHAR(20) DEFAULT 'DRAFT',

    -- Audit
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

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

-- ----------------------------------------------------------
-- SECTION 2: Contract service pricing items
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_contract_pricing_items (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT NOT NULL,
    medical_service_id  BIGINT,
    service_category    VARCHAR(100),
    unit_price          NUMERIC(15,2) NOT NULL,
    effective_from      DATE,
    effective_to        DATE,
    active              BOOLEAN DEFAULT true,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(255),

    CONSTRAINT fk_pricing_contract FOREIGN KEY (contract_id)         REFERENCES provider_contracts(id)  ON DELETE CASCADE,
    CONSTRAINT fk_pricing_service  FOREIGN KEY (medical_service_id)  REFERENCES medical_services(id)    ON DELETE RESTRICT
);

-- ----------------------------------------------------------
-- SECTION 3: Provider network tiers
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS network_providers (
    id          BIGSERIAL PRIMARY KEY,
    employer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    network_tier VARCHAR(50) CHECK (network_tier IN ('TIER_1','TIER_2','TIER_3','OUT_OF_NETWORK')),
    effective_date DATE NOT NULL,
    expiry_date    DATE,
    active         BOOLEAN DEFAULT true,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(255),

    CONSTRAINT fk_network_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_network_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_network_provider ON network_providers(employer_id, provider_id) WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_network_employer ON network_providers(employer_id);
CREATE INDEX IF NOT EXISTS idx_network_provider ON network_providers(provider_id);
CREATE INDEX IF NOT EXISTS idx_network_tier     ON network_providers(network_tier);

-- ----------------------------------------------------------
-- SECTION 4: Legacy provider contract pricing (backward compat)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS legacy_provider_contracts (
    id              BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT NOT NULL,
    service_code    VARCHAR(50)   NOT NULL,
    contract_price  NUMERIC(10,2) NOT NULL,
    currency        VARCHAR(3) DEFAULT 'LYD',
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    active          BOOLEAN DEFAULT true,
    notes           VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_legacy_contract_service_date
    ON legacy_provider_contracts(provider_id, service_code, effective_from);
CREATE INDEX IF NOT EXISTS idx_legacy_contracts_provider ON legacy_provider_contracts(provider_id);
CREATE INDEX IF NOT EXISTS idx_legacy_contracts_service  ON legacy_provider_contracts(service_code);
CREATE INDEX IF NOT EXISTS idx_legacy_contracts_active   ON legacy_provider_contracts(active);
