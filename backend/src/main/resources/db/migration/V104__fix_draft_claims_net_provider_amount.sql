-- V104: Fix DRAFT claims that have approved_amount set but null net_provider_amount
-- These are batch-entry (backlog) claims created directly as processed.
-- net_provider_amount should equal approved_amount for these claims.

UPDATE claims
SET net_provider_amount = approved_amount,
    updated_at = NOW()
WHERE active = true
  AND status = 'DRAFT'
  AND approved_amount IS NOT NULL
  AND approved_amount > 0
  AND (net_provider_amount IS NULL OR net_provider_amount = 0);
