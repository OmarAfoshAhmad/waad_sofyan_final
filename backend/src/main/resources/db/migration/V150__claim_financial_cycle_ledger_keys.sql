-- A claim may be approved, reversed for correction, and approved again.
-- Financial idempotency is therefore one entry per calculation cycle, not one
-- entry for the entire lifetime of the claim. Legacy rows remain valid with a
-- NULL version; every new finance-constitution row carries the explicit cycle.
CREATE UNIQUE INDEX ux_account_transactions_claim_approval_cycle
    ON account_transactions (reference_id, reference_version)
    WHERE reference_type = 'CLAIM_APPROVAL' AND reference_version IS NOT NULL;

CREATE UNIQUE INDEX ux_account_transactions_claim_reversal_cycle
    ON account_transactions (reference_id, reference_version)
    WHERE reference_type = 'CLAIM_REVERSAL' AND reference_version IS NOT NULL;

COMMENT ON COLUMN account_transactions.reference_version IS
    'Financial cycle key. CLAIM_APPROVAL and CLAIM_REVERSAL use ClaimLine.calculationVersion; CLAIM_AMOUNT_ADJUSTMENT uses Claim.version.';
