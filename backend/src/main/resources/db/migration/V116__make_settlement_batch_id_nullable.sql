-- =============================================================================
-- V116: Make provider_payments.settlement_batch_id nullable
-- =============================================================================
-- Reason: Installment payments (partial payments from provider balance) are not
-- linked to a specific settlement batch. The NOT NULL constraint was originally
-- designed for batch-only payments, but the system supports direct installment
-- payments that have no batch context.
-- =============================================================================

-- Remove NOT NULL constraint (keep the column and the FK/unique constraint intact)
ALTER TABLE provider_payments
    ALTER COLUMN settlement_batch_id DROP NOT NULL;
