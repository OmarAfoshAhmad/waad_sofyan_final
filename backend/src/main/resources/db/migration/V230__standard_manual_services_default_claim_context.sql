ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS default_claim_context_code VARCHAR(60);

ALTER TABLE medical_services
    DROP CONSTRAINT IF EXISTS fk_medical_services_default_claim_context;

ALTER TABLE medical_services
    ADD CONSTRAINT fk_medical_services_default_claim_context
    FOREIGN KEY (default_claim_context_code) REFERENCES claim_contexts(code);

UPDATE medical_services
SET default_claim_context_code = 'OUTPATIENT'
WHERE pricing_mode = 'MANUAL_AMOUNT'
  AND default_claim_context_code IS NULL;

CREATE INDEX IF NOT EXISTS idx_medical_services_default_claim_context
    ON medical_services(default_claim_context_code)
    WHERE pricing_mode = 'MANUAL_AMOUNT';

COMMENT ON COLUMN medical_services.default_claim_context_code IS
    'Administrative default context for standard manual-amount services. Coverage still uses the claim actual claim_context_code plus category rules.';
