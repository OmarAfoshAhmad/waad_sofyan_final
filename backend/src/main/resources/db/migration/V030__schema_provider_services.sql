-- ============================================================
-- V030: Provider services directory and medical reviewer assignments
-- ============================================================
-- Depends on: V006 (providers), V010 (users)

-- ----------------------------------------------------------
-- SECTION 1: Provider active service directory
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_services (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
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

-- ----------------------------------------------------------
-- SECTION 2: Medical reviewer ↔ provider assignments
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS medical_reviewer_providers (
    id          BIGSERIAL PRIMARY KEY,
    reviewer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(255),

    CONSTRAINT fk_mrp_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id)     ON DELETE RESTRICT,
    CONSTRAINT fk_mrp_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT uk_reviewer_provider UNIQUE (reviewer_id, provider_id)
);

CREATE INDEX IF NOT EXISTS idx_mrp_reviewer_id ON medical_reviewer_providers(reviewer_id);
CREATE INDEX IF NOT EXISTS idx_mrp_provider_id ON medical_reviewer_providers(provider_id);
CREATE INDEX IF NOT EXISTS idx_mrp_active       ON medical_reviewer_providers(active);

-- ----------------------------------------------------------
-- SECTION 3: Provider service price import log
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_service_price_import_logs (
    id                  BIGSERIAL PRIMARY KEY,
    import_batch_id     VARCHAR(64) NOT NULL UNIQUE,
    provider_id         BIGINT NOT NULL,
    provider_code       VARCHAR(100) NOT NULL,
    provider_name       VARCHAR(255) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    file_size_bytes     BIGINT,
    import_mode         VARCHAR(20) NOT NULL DEFAULT 'REPLACE',
    total_rows          INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    error_count         INTEGER DEFAULT 0,
    skipped_count       INTEGER DEFAULT 0,
    status              VARCHAR(20) DEFAULT 'PENDING',
    error_details       JSONB,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    processing_time_ms  BIGINT,
    imported_by_user_id BIGINT,
    imported_by_username VARCHAR(100),
    created_at          TIMESTAMP
);
