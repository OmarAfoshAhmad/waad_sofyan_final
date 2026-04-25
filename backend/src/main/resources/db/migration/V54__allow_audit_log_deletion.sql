-- =============================================================================
-- V54: Allow SUPER_ADMIN deletion of audit log records
-- 
-- This migration removes the database-level DELETE trigger on medical_audit_logs
-- to allow authorized administrators to purge old audit records.
-- The UPDATE trigger is preserved to keep records immutable (no modification).
-- 
-- Security: Deletion is still protected at the application level via:
--   1. SUPER_ADMIN role-only access (@PreAuthorize)
--   2. Password verification before any deletion is executed
-- =============================================================================

-- Remove the delete prevention trigger (keep the update prevention trigger)
DROP TRIGGER IF EXISTS trg_no_delete_medical_audit_logs ON medical_audit_logs;

-- Update the trigger function to only mention UPDATE (not DELETE) in its message
CREATE OR REPLACE FUNCTION prevent_medical_audit_logs_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'medical_audit_logs is immutable: UPDATE operation is not allowed. Use the admin API for deletion.';
END;
$$ LANGUAGE plpgsql;
