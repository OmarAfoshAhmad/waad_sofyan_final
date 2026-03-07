-- ============================================================================
-- V17: Align missing runtime columns for claims and provider_contracts
-- ============================================================================

-- provider_contracts.active used by dashboard contract counts
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

UPDATE provider_contracts
SET active = true
WHERE active IS NULL;

ALTER TABLE provider_contracts
    ALTER COLUMN active SET DEFAULT true;

CREATE INDEX IF NOT EXISTS idx_provider_contracts_active ON provider_contracts(active);

-- claims.pre_authorization_id used by Claim entity joins
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS pre_authorization_id BIGINT;

-- Backfill from legacy naming if available
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'claims' AND column_name = 'pre_approval_id'
    ) THEN
        UPDATE claims
        SET pre_authorization_id = pre_approval_id
        WHERE pre_authorization_id IS NULL AND pre_approval_id IS NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_claims_pre_authorization_id ON claims(pre_authorization_id);

-- ============================================================================
-- Migration Complete: V17
-- ============================================================================
