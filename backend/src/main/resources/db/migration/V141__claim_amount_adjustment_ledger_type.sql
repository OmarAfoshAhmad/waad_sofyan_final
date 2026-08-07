-- Phase 7.1 — handleClaimAmountAdjusted previously used account.debit() when an
-- approved claim's amount was reduced, raising totalPaid as if a payment had
-- occurred. This gives claim-amount corrections their own reference type and a
-- durable idempotency key, instead of the generic ADJUSTMENT (already retired
-- from live use in V140/this phase) or a bare claim_id (a claim can legitimately
-- be adjusted more than once, so claim_id alone cannot tell two adjustments
-- apart or a redelivered one from a fresh one).
ALTER TABLE account_transactions
    ADD COLUMN reference_version BIGINT;

COMMENT ON COLUMN account_transactions.reference_version IS
    'Optional secondary key completing reference_id for reference types whose natural key is (reference_id, version) rather than reference_id alone — currently only CLAIM_AMOUNT_ADJUSTMENT, keyed by the claim''s @Version after the amount change that triggered it.';

ALTER TABLE account_transactions
    DROP CONSTRAINT IF EXISTS account_transactions_reference_type_check;

ALTER TABLE account_transactions
    ADD CONSTRAINT account_transactions_reference_type_check
    CHECK (reference_type IN (
        'CLAIM_APPROVAL',
        'CLAIM_REVERSAL',
        'CLAIM_SETTLEMENT',
        'CLAIM_AMOUNT_ADJUSTMENT',
        'SETTLEMENT_PAYMENT',
        'PROVIDER_PAYMENT',
        'PROVIDER_PAYMENT_REVERSAL',
        'ADJUSTMENT'
    ));

-- Redelivering the same (claim_id, claim_version) adjustment must be rejected by
-- the database, not merely by an application-level pre-check that a race could
-- slip past.
CREATE UNIQUE INDEX ux_account_transactions_claim_amount_adjustment
    ON account_transactions (reference_id, reference_version)
    WHERE reference_type = 'CLAIM_AMOUNT_ADJUSTMENT';
