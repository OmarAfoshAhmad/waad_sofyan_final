-- V19: Align canonical_medical_services schema with CanonicalMedicalService entity for ddl-auto=validate

-- Missing in legacy schema but required by entity
ALTER TABLE canonical_medical_services
    ADD COLUMN IF NOT EXISTS description TEXT;

-- Entity maps to these names, while legacy table uses category_level_1/2
ALTER TABLE canonical_medical_services
    ADD COLUMN IF NOT EXISTS level_1_care VARCHAR(100);

ALTER TABLE canonical_medical_services
    ADD COLUMN IF NOT EXISTS level_2_domain VARCHAR(100);

-- Safe backfill from legacy columns when available
UPDATE canonical_medical_services
SET level_1_care = COALESCE(level_1_care, category_level_1)
WHERE level_1_care IS NULL;

UPDATE canonical_medical_services
SET level_2_domain = COALESCE(level_2_domain, category_level_2)
WHERE level_2_domain IS NULL;

-- Ensure non-null required by entity (use fallback value if historical row lacks source)
UPDATE canonical_medical_services
SET level_1_care = 'UNCLASSIFIED'
WHERE level_1_care IS NULL;

UPDATE canonical_medical_services
SET level_2_domain = 'UNCLASSIFIED'
WHERE level_2_domain IS NULL;

ALTER TABLE canonical_medical_services
    ALTER COLUMN level_1_care SET NOT NULL;

ALTER TABLE canonical_medical_services
    ALTER COLUMN level_2_domain SET NOT NULL;
