-- Migration: Add missing financial audit fields to claim_lines
-- These fields were added to the ClaimLine entity but not yet to the database schema.

ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS approved_amount DECIMAL(15, 2);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS company_share DECIMAL(15, 2);
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS patient_share DECIMAL(15, 2);

-- Also add requested_total if not present (referenced in error log insert statement)
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS requested_total DECIMAL(15, 2);

-- Ensure correct types and scales
ALTER TABLE claim_lines ALTER COLUMN approved_amount TYPE DECIMAL(15, 2);
ALTER TABLE claim_lines ALTER COLUMN company_share TYPE DECIMAL(15, 2);
ALTER TABLE claim_lines ALTER COLUMN patient_share TYPE DECIMAL(15, 2);
ALTER TABLE claim_lines ALTER COLUMN requested_total TYPE DECIMAL(15, 2);
