-- ============================================================================
-- V86: Add Medical Specialties Table
-- ============================================================================
-- Purpose  : Create the medical_specialties lookup table to support a
--            specialty-aware unified medical dictionary.
-- Rules    : Additive only — no DROP, no DELETE, no ALTER on existing tables.
-- ============================================================================

CREATE TABLE IF NOT EXISTS medical_specialties (
    id       BIGSERIAL PRIMARY KEY,
    code     VARCHAR(50)  NOT NULL,
    name_ar  VARCHAR(255) NOT NULL,
    name_en  VARCHAR(255),
    deleted  BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_medical_specialties_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_medical_specialties_deleted
    ON medical_specialties (deleted);
