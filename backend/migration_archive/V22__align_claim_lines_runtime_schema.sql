-- V22: Align claim_lines schema with ClaimLine entity for ddl-auto=validate

-- Entity-required columns missing in legacy table
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS medical_service_id BIGINT;
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS service_name VARCHAR(255);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS service_category_id BIGINT;
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS service_category_name VARCHAR(200);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS total_price NUMERIC(15,2);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS requires_pa BOOLEAN;
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS coverage_percent_snapshot INTEGER;
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS patient_copay_percent_snapshot INTEGER;

-- Backfill from legacy columns where possible
UPDATE claim_lines
SET medical_service_id = COALESCE(medical_service_id, canonical_service_id)
WHERE medical_service_id IS NULL;

UPDATE claim_lines
SET service_name = COALESCE(service_name, service_description)
WHERE service_name IS NULL;

UPDATE claim_lines
SET total_price = COALESCE(total_price, total_amount, (quantity::numeric * unit_price))
WHERE total_price IS NULL;

UPDATE claim_lines
SET requires_pa = COALESCE(requires_pa, false)
WHERE requires_pa IS NULL;

UPDATE claim_lines
SET service_code = COALESCE(service_code, 'UNKNOWN')
WHERE service_code IS NULL;

UPDATE claim_lines
SET service_category_id = COALESCE(service_category_id, 0)
WHERE service_category_id IS NULL;

-- Enforce entity constraints
ALTER TABLE claim_lines ALTER COLUMN medical_service_id SET NOT NULL;
ALTER TABLE claim_lines ALTER COLUMN service_code SET NOT NULL;
ALTER TABLE claim_lines ALTER COLUMN service_category_id SET NOT NULL;
ALTER TABLE claim_lines ALTER COLUMN total_price SET NOT NULL;
ALTER TABLE claim_lines ALTER COLUMN requires_pa SET DEFAULT false;
ALTER TABLE claim_lines ALTER COLUMN requires_pa SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_claim_line_service ON claim_lines(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_claim_line_claim ON claim_lines(claim_id);
