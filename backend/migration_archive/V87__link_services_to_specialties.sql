-- ============================================================================
-- V87: Link medical_services → medical_specialties
-- ============================================================================
-- Purpose  : Add nullable specialty_id FK to medical_services.
-- Rules    : Additive only. Column is nullable (safe evolution, existing
--            rows remain valid). No back-fill, no NOT NULL enforcement.
-- ============================================================================

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS specialty_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_medical_service_specialty'
          AND table_name      = 'medical_services'
    ) THEN
        ALTER TABLE medical_services
            ADD CONSTRAINT fk_medical_service_specialty
            FOREIGN KEY (specialty_id)
            REFERENCES medical_specialties (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_medical_services_specialty_id
    ON medical_services (specialty_id)
    WHERE specialty_id IS NOT NULL;
