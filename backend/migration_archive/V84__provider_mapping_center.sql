-- ============================================================================
-- V84: Provider Mapping Center
-- ============================================================================
-- Creates the three-table provider service mapping infrastructure:
--   1. provider_raw_services    - raw service names ingested from providers
--   2. provider_service_mappings - resolved mappings to canonical medical_services
--   3. provider_mapping_audit   - full audit trail of mapping decisions
--
-- Constraints:
--   - No changes to medical_services, provider_contract_pricing_items, claims
--   - Additive only
-- ============================================================================

-- ============================================================================
-- TABLE 1: provider_raw_services
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_raw_services (
    id                BIGSERIAL    PRIMARY KEY,
    provider_id       BIGINT       NOT NULL
                          REFERENCES providers(id) ON DELETE CASCADE,
    raw_name          VARCHAR(500) NOT NULL,
    normalized_name   VARCHAR(500),
    code              VARCHAR(100),
    encounter_type    VARCHAR(20),
    source            VARCHAR(50),
    import_batch_id   BIGINT,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    confidence_score  NUMERIC(5,2),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_prs_provider_raw_name UNIQUE (provider_id, raw_name)
);

CREATE INDEX IF NOT EXISTS idx_prs_provider ON provider_raw_services(provider_id);
CREATE INDEX IF NOT EXISTS idx_prs_status   ON provider_raw_services(status);

COMMENT ON TABLE provider_raw_services IS
    'Raw service names imported from providers, awaiting mapping to medical_services';
COMMENT ON COLUMN provider_raw_services.status IS
    'PENDING | AUTO_MATCHED | MANUAL_CONFIRMED | REJECTED';

-- ============================================================================
-- TABLE 2: provider_service_mappings
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_service_mappings (
    id                      BIGSERIAL   PRIMARY KEY,
    provider_raw_service_id BIGINT      NOT NULL
                                REFERENCES provider_raw_services(id) ON DELETE CASCADE,
    medical_service_id      BIGINT      NOT NULL
                                REFERENCES medical_services(id) ON DELETE RESTRICT,
    mapping_status          VARCHAR(30) NOT NULL,
    mapped_by               BIGINT      REFERENCES users(id),
    mapped_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confidence_score        NUMERIC(5,2),

    CONSTRAINT uq_psm_raw_service UNIQUE (provider_raw_service_id)
);

CREATE INDEX IF NOT EXISTS idx_psm_medical_service ON provider_service_mappings(medical_service_id);

COMMENT ON TABLE provider_service_mappings IS
    'Resolved mappings from provider_raw_services to canonical medical_services';

-- ============================================================================
-- TABLE 3: provider_mapping_audit
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_mapping_audit (
    id                      BIGSERIAL   PRIMARY KEY,
    provider_raw_service_id BIGINT      REFERENCES provider_raw_services(id),
    action                  VARCHAR(50) NOT NULL,
    old_value               TEXT,
    new_value               TEXT,
    performed_by            BIGINT      REFERENCES users(id),
    performed_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pma_raw_service ON provider_mapping_audit(provider_raw_service_id);

COMMENT ON TABLE provider_mapping_audit IS
    'Immutable audit trail: AUTO_MATCH, MANUAL_MAP, REJECT events';

-- ============================================================================
-- Migration Complete: V84
-- ============================================================================
