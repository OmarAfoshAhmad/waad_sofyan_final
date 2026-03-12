-- ============================================================
-- Table: medical_reviewer_providers
-- Depends on: users, providers
-- ============================================================
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
