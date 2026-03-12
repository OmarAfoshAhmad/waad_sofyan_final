-- ============================================================
-- Table: medical_specialties
-- Depends on: (none)
-- ============================================================
CREATE TABLE IF NOT EXISTS medical_specialties (
    id       BIGSERIAL PRIMARY KEY,
    code     VARCHAR(50)  NOT NULL UNIQUE,
    name_ar  VARCHAR(255) NOT NULL,
    name_en  VARCHAR(255),
    deleted  BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_medical_specialties_deleted ON medical_specialties(deleted) WHERE deleted = false;
