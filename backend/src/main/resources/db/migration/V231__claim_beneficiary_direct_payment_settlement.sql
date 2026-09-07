ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS beneficiary_paid_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS beneficiary_paid_toward_copay NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS beneficiary_paid_toward_refusal NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS provider_refusal_balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE claims
    DROP CONSTRAINT IF EXISTS chk_claims_beneficiary_paid_amount,
    DROP CONSTRAINT IF EXISTS chk_claims_beneficiary_paid_toward_copay,
    DROP CONSTRAINT IF EXISTS chk_claims_beneficiary_paid_toward_refusal,
    DROP CONSTRAINT IF EXISTS chk_claims_provider_refusal_balance;

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_beneficiary_paid_amount
        CHECK (beneficiary_paid_amount >= 0),
    ADD CONSTRAINT chk_claims_beneficiary_paid_toward_copay
        CHECK (beneficiary_paid_toward_copay >= 0),
    ADD CONSTRAINT chk_claims_beneficiary_paid_toward_refusal
        CHECK (beneficiary_paid_toward_refusal >= 0),
    ADD CONSTRAINT chk_claims_provider_refusal_balance
        CHECK (provider_refusal_balance >= 0);

COMMENT ON COLUMN claims.beneficiary_paid_amount IS
    'Optional direct amount paid by the beneficiary outside insurance settlement. It does not change coverage, limits, refused amount, patient copay, or insurer net amount.';
COMMENT ON COLUMN claims.beneficiary_paid_toward_copay IS
    'Part of beneficiary_paid_amount applied to the beneficiary original copay/patient share.';
COMMENT ON COLUMN claims.beneficiary_paid_toward_refusal IS
    'Excess beneficiary payment applied against refused amount.';
COMMENT ON COLUMN claims.provider_refusal_balance IS
    'Remaining refused amount after beneficiary-paid excess is applied; this is the provider-side refusal balance.';
