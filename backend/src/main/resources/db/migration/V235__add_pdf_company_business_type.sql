ALTER TABLE pdf_company_settings
ADD COLUMN IF NOT EXISTS company_business_type VARCHAR(255);
