-- ============================================================
-- Table: provider_services
-- Depends on: providers
-- ============================================================
CREATE TABLE IF NOT EXISTS provider_services (
    id           BIGSERIAL PRIMARY KEY,
    provider_id  BIGINT NOT NULL,
    service_code VARCHAR(50) NOT NULL,
    active       BOOLEAN DEFAULT true,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_provider_services_provider FOREIGN KEY (provider_id)
        REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT unique_provider_service UNIQUE (provider_id, service_code)
);

CREATE INDEX IF NOT EXISTS idx_provider_services_provider ON provider_services(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_services_code     ON provider_services(service_code);
CREATE INDEX IF NOT EXISTS idx_provider_services_active   ON provider_services(active);
