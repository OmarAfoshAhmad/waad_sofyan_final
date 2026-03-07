-- V31: Align medical_packages table with MedicalPackage entity for ddl-auto=validate

ALTER TABLE medical_packages
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

ALTER TABLE medical_packages
    ADD COLUMN IF NOT EXISTS name VARCHAR(255);

ALTER TABLE medical_packages
    ADD COLUMN IF NOT EXISTS total_coverage_limit DOUBLE PRECISION;

-- Backfill from legacy columns
UPDATE medical_packages
SET code = COALESCE(code, package_code)
WHERE code IS NULL;

UPDATE medical_packages
SET name = COALESCE(name, package_name)
WHERE name IS NULL;

-- Enforce required columns from entity
UPDATE medical_packages
SET code = 'PKG-' || id
WHERE code IS NULL;

UPDATE medical_packages
SET name = 'UNNAMED-PACKAGE-' || id
WHERE name IS NULL;

ALTER TABLE medical_packages
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE medical_packages
    ALTER COLUMN name SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_medical_package_code'
          AND conrelid = 'medical_packages'::regclass
    ) THEN
        ALTER TABLE medical_packages
            ADD CONSTRAINT uk_medical_package_code UNIQUE (code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_medical_packages_code_runtime ON medical_packages(code);
