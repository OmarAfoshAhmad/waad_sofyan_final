-- ============================================================================
-- V76: Standardize Core Financial Column Precision
-- ============================================================================
-- Phase 2A: Fix monetary precision inconsistency in claims and claim_lines
-- 
-- BEFORE: requested_amount, approved_amount, paid_amount, patient_share = NUMERIC(12,2)
-- AFTER:  All core financial columns = NUMERIC(15,2)
--
-- This is a SAFE widening operation (12,2 → 15,2):
--   - No data loss possible
--   - No rounding changes  
--   - Existing values fit within new precision
-- ============================================================================

-- ═══════════════════════════════════════════════════════════════════════════
-- CLAIMS TABLE - Core financial columns
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE claims 
    ALTER COLUMN requested_amount TYPE NUMERIC(15,2);

ALTER TABLE claims 
    ALTER COLUMN approved_amount TYPE NUMERIC(15,2);

ALTER TABLE claims 
    ALTER COLUMN paid_amount TYPE NUMERIC(15,2);

ALTER TABLE claims 
    ALTER COLUMN patient_share TYPE NUMERIC(15,2);

-- ═══════════════════════════════════════════════════════════════════════════
-- CLAIM_LINES TABLE - Line-level financial columns
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE claim_lines 
    ALTER COLUMN total_amount TYPE NUMERIC(15,2);

ALTER TABLE claim_lines 
    ALTER COLUMN approved_amount TYPE NUMERIC(15,2);

ALTER TABLE claim_lines 
    ALTER COLUMN unit_price TYPE NUMERIC(15,2);

-- ============================================================================
-- VERIFICATION: After migration, all 7 columns should be NUMERIC(15,2)
-- Run: SELECT column_name, numeric_precision, numeric_scale 
--      FROM information_schema.columns 
--      WHERE table_name IN ('claims','claim_lines') 
--      AND column_name IN ('requested_amount','approved_amount','paid_amount',
--                          'patient_share','total_amount','unit_price');
-- ============================================================================
