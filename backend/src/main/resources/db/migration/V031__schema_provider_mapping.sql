-- ============================================================
-- V031: Provider service mapping center
-- ============================================================
-- Allows mapping raw provider service names → canonical medical_services.
-- Depends on: V006 (providers), V010 (users), V021 (medical_services)

-- ----------------------------------------------------------
-- SECTION 1: Raw provider service names (as submitted by provider)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_raw_services (
    id              BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT NOT NULL,
    raw_name        VARCHAR(500) NOT NULL,
    normalized_name VARCHAR(500),
    code            VARCHAR(100),
    encounter_type  VARCHAR(20),
    source          VARCHAR(50),
    import_batch_id BIGINT,
    status          VARCHAR(30) DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','AUTO_MATCHED','MANUAL_CONFIRMED','REJECTED')),
    confidence_score NUMERIC(5,2),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_prs_provider      FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT uq_prs_provider_name UNIQUE (provider_id, raw_name)
);

CREATE INDEX IF NOT EXISTS idx_prs_provider ON provider_raw_services(provider_id);
CREATE INDEX IF NOT EXISTS idx_prs_status   ON provider_raw_services(status);

-- ----------------------------------------------------------
-- SECTION 2: Confirmed service mappings (raw → canonical)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_service_mappings (
    id                      BIGSERIAL PRIMARY KEY,
    provider_raw_service_id BIGINT NOT NULL,
    medical_service_id      BIGINT NOT NULL,
    mapping_status          VARCHAR(30) NOT NULL,
    mapped_by               BIGINT,
    mapped_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confidence_score        NUMERIC(5,2),

    CONSTRAINT fk_psm_raw     FOREIGN KEY (provider_raw_service_id) REFERENCES provider_raw_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_psm_service FOREIGN KEY (medical_service_id)      REFERENCES medical_services(id)       ON DELETE RESTRICT,
    CONSTRAINT fk_psm_user    FOREIGN KEY (mapped_by)               REFERENCES users(id),
    CONSTRAINT uq_psm_raw_service UNIQUE (provider_raw_service_id)
);

CREATE INDEX IF NOT EXISTS idx_psm_medical_service ON provider_service_mappings(medical_service_id);

-- ----------------------------------------------------------
-- SECTION 3: Mapping audit trail
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_mapping_audit (
    id                      BIGSERIAL PRIMARY KEY,
    provider_raw_service_id BIGINT,
    action                  VARCHAR(50) NOT NULL,
    old_value               TEXT,
    new_value               TEXT,
    performed_by            BIGINT,
    performed_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pma_raw  FOREIGN KEY (provider_raw_service_id) REFERENCES provider_raw_services(id),
    CONSTRAINT fk_pma_user FOREIGN KEY (performed_by)            REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_pma_raw_service ON provider_mapping_audit(provider_raw_service_id);
