-- Add annual_deductible column to benefit_policies
ALTER TABLE benefit_policies
    ADD COLUMN IF NOT EXISTS annual_deductible DECIMAL(15,2) NOT NULL DEFAULT 0.00;

COMMENT ON COLUMN benefit_policies.annual_deductible IS 
    'Amount member must pay before insurance coverage begins (per year). 0 = no deductible.';
