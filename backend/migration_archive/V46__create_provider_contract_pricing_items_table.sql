-- Create missing table required by ProviderContractPricingItem entity

CREATE TABLE IF NOT EXISTS provider_contract_pricing_items (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    medical_service_id BIGINT,
    service_name VARCHAR(255),
    service_code VARCHAR(50),
    category_name VARCHAR(255),
    quantity INTEGER DEFAULT 0,
    medical_category_id BIGINT,
    base_price NUMERIC(15,2),
    contract_price NUMERIC(15,2),
    discount_percent NUMERIC(5,2),
    unit VARCHAR(50),
    currency VARCHAR(3),
    effective_from DATE,
    effective_to DATE,
    notes VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_pricing_item_contract
        FOREIGN KEY (contract_id) REFERENCES provider_contracts(id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_item_medical_service
        FOREIGN KEY (medical_service_id) REFERENCES medical_services(id) ON DELETE SET NULL,
    CONSTRAINT fk_pricing_item_medical_category
        FOREIGN KEY (medical_category_id) REFERENCES medical_categories(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pricing_contract_id ON provider_contract_pricing_items(contract_id);
CREATE INDEX IF NOT EXISTS idx_pricing_service_id ON provider_contract_pricing_items(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_pricing_category_id ON provider_contract_pricing_items(medical_category_id);
CREATE INDEX IF NOT EXISTS idx_pricing_active ON provider_contract_pricing_items(active);
CREATE INDEX IF NOT EXISTS idx_pricing_service_name ON provider_contract_pricing_items(service_name);
