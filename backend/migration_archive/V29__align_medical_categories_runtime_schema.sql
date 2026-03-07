-- V29: Align medical_categories table with MedicalCategory entity for ddl-auto=validate

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS name VARCHAR(200);

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS parent_id BIGINT;

-- Backfill from legacy columns
UPDATE medical_categories
SET code = COALESCE(code, category_code)
WHERE code IS NULL;

UPDATE medical_categories
SET name = COALESCE(name, category_name)
WHERE name IS NULL;

-- Ensure required non-null columns from entity
UPDATE medical_categories
SET code = 'CAT-' || id
WHERE code IS NULL;

UPDATE medical_categories
SET name = 'UNNAMED-' || id
WHERE name IS NULL;

ALTER TABLE medical_categories
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE medical_categories
    ALTER COLUMN name SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_medical_categories_code'
          AND conrelid = 'medical_categories'::regclass
    ) THEN
        ALTER TABLE medical_categories
            ADD CONSTRAINT uk_medical_categories_code UNIQUE (code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_medical_categories_code_runtime ON medical_categories(code);
CREATE INDEX IF NOT EXISTS idx_medical_categories_parent_id ON medical_categories(parent_id);
