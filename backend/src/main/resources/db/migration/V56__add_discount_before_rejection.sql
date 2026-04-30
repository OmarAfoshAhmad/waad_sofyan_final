-- Add discount_before_rejection flag to provider_contracts and claims
-- This allows configuring per-contract whether the discount is applied
-- before or after rejection deduction from the provider's share.
-- Co-pay is ALWAYS calculated first from the gross amount.

-- Provider Contract: the configuration source
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS discount_before_rejection BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN provider_contracts.discount_before_rejection IS
    'true = خصم نسبة التخفيض قبل خصم المرفوض, false = بعده (الافتراضي)';

-- Claim: snapshot of the setting at approval time
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS discount_before_rejection BOOLEAN;

COMMENT ON COLUMN claims.discount_before_rejection IS
    'لقطة لإعداد توقيت الخصم من العقد عند اعتماد المطالبة';
