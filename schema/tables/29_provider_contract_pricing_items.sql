-- ============================================================
-- Table: provider_contract_pricing_items
-- Depends on: provider_contracts, medical_services
-- ============================================================
CREATE TABLE IF NOT EXISTS provider_contract_pricing_items (
    id                 BIGSERIAL PRIMARY KEY,
    contract_id        BIGINT NOT NULL,
    medical_service_id BIGINT,
    service_category   VARCHAR(100),
    unit_price         NUMERIC(15,2) NOT NULL,
    effective_from     DATE,
    effective_to       DATE,
    active             BOOLEAN DEFAULT true,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP,
    created_by         VARCHAR(255),

    CONSTRAINT fk_pricing_contract FOREIGN KEY (contract_id)
        REFERENCES provider_contracts(id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_service FOREIGN KEY (medical_service_id)
        REFERENCES medical_services(id) ON DELETE RESTRICT
);
