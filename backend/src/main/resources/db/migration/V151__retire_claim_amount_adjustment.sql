-- Final claims are immutable. A correction is a full financial reversal followed
-- by a new calculation cycle and approval, never an in-place amount adjustment.
DROP INDEX IF EXISTS ux_account_transactions_claim_amount_adjustment;

ALTER TABLE account_transactions
    DROP CONSTRAINT IF EXISTS account_transactions_reference_type_check;

ALTER TABLE account_transactions
    ADD CONSTRAINT account_transactions_reference_type_check
    CHECK (reference_type IN (
        'CLAIM_APPROVAL',
        'CLAIM_REVERSAL',
        'CLAIM_SETTLEMENT',
        'SETTLEMENT_PAYMENT',
        'PROVIDER_PAYMENT',
        'PROVIDER_PAYMENT_REVERSAL',
        'ADJUSTMENT'
    ));

COMMENT ON COLUMN account_transactions.reference_version IS
    'Financial calculation-cycle key used by CLAIM_APPROVAL and CLAIM_REVERSAL.';
