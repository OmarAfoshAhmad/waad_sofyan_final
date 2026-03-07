-- V33: Align medical_services table with MedicalService entity for ddl-auto=validate

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS name VARCHAR(200);

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS base_price NUMERIC(10,2);

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS requires_pa BOOLEAN;

-- Backfill from legacy columns
UPDATE medical_services
SET code = COALESCE(code, service_code)
WHERE code IS NULL;

UPDATE medical_services
SET name = COALESCE(name, service_name)
WHERE name IS NULL;

UPDATE medical_services
SET status = COALESCE(status, CASE WHEN active THEN 'ACTIVE' ELSE 'ARCHIVED' END)
WHERE status IS NULL;

UPDATE medical_services
SET requires_pa = COALESCE(requires_pa, true)
WHERE requires_pa IS NULL;

-- Ensure mandatory fields required by entity
UPDATE medical_services
SET code = 'SRV-' || id
WHERE code IS NULL;

UPDATE medical_services
SET name = 'UNNAMED-SERVICE-' || id
WHERE name IS NULL;

UPDATE medical_services
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE medical_services
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE medical_services
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE medical_services
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE medical_services
    ALTER COLUMN requires_pa SET NOT NULL;

ALTER TABLE medical_services
    ALTER COLUMN requires_pa SET DEFAULT true;

-- Entity allows category_id to be nullable (DRAFT services)
ALTER TABLE medical_services
    ALTER COLUMN category_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_medical_services_code_runtime'
          AND conrelid = 'medical_services'::regclass
    ) THEN
        ALTER TABLE medical_services
            ADD CONSTRAINT uk_medical_services_code_runtime UNIQUE (code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_medical_services_code_runtime ON medical_services(code);
CREATE INDEX IF NOT EXISTS idx_medical_services_status ON medical_services(status);
