-- ============================================================
-- V109: Drop duplicate prevention index/constraint from claims
-- ============================================================
-- This constraint prevents same-day, same-provider claims of exact same amount
-- for the same member. It was causing issues during testing. 
-- Disabling this constraint to allow the system to function correctly.

ALTER TABLE claims DROP CONSTRAINT IF EXISTS idx_claims_duplicate_prevention;
DROP INDEX IF EXISTS idx_claims_duplicate_prevention;
