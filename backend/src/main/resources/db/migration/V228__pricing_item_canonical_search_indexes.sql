CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_pricing_items_service_code_canonical_trgm
    ON provider_contract_pricing_items USING GIN (waad_search_normalize(service_code) gin_trgm_ops)
    WHERE service_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pricing_items_service_name_canonical_trgm
    ON provider_contract_pricing_items USING GIN (waad_search_normalize(service_name) gin_trgm_ops)
    WHERE service_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pricing_items_category_name_canonical_trgm
    ON provider_contract_pricing_items USING GIN (waad_search_normalize(category_name) gin_trgm_ops)
    WHERE category_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_medical_services_name_canonical_trgm
    ON medical_services USING GIN (waad_search_normalize(name) gin_trgm_ops)
    WHERE name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_medical_services_code_canonical_trgm
    ON medical_services USING GIN (waad_search_normalize(code) gin_trgm_ops)
    WHERE code IS NOT NULL;
