-- ============================================================
-- V108: Make Specialty Nullable in Medical Services
-- ============================================================
-- The user decided not to enforce medical specialty classification 
-- during service creation in the contract pricing context.

-- 1. Remove NOT NULL constraint
ALTER TABLE medical_services ALTER COLUMN specialty_id DROP NOT NULL;

-- 2. Drop the check constraint if it exists
ALTER TABLE medical_services DROP CONSTRAINT IF EXISTS chk_service_has_specialty;
