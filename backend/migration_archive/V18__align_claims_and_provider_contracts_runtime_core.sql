-- V18: Final runtime-core alignment for claims and provider contracts

-- provider_contracts.status is required by Dashboard queries and ModernProviderContract entity
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE provider_contracts
SET status = COALESCE(
    status,
    CASE
        WHEN contract_status IS NOT NULL THEN contract_status
        WHEN active THEN 'ACTIVE'
        ELSE 'DRAFT'
    END
)
WHERE status IS NULL;

ALTER TABLE provider_contracts
    ALTER COLUMN status SET DEFAULT 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_provider_contracts_status ON provider_contracts(status);

-- claims runtime columns required by Claim entity/read queries
ALTER TABLE claims ADD COLUMN IF NOT EXISTS reviewer_comment TEXT;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS doctor_name VARCHAR(255);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS difference_amount NUMERIC(15,2);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS patient_copay NUMERIC(15,2);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS net_provider_amount NUMERIC(15,2);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS copay_percent NUMERIC(5,2);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS deductible_applied NUMERIC(15,2);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(100);
ALTER TABLE claims ADD COLUMN IF NOT EXISTS settled_at TIMESTAMP;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS settlement_notes TEXT;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS expected_completion_date DATE;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS actual_completion_date DATE;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS within_sla BOOLEAN;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS business_days_taken INTEGER;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS sla_days_configured INTEGER;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS service_count INTEGER;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS attachments_count INTEGER;

CREATE INDEX IF NOT EXISTS idx_claims_actual_completion_date ON claims(actual_completion_date);
CREATE INDEX IF NOT EXISTS idx_claims_within_sla ON claims(within_sla);
CREATE INDEX IF NOT EXISTS idx_claims_pre_authorization_id ON claims(pre_authorization_id);
