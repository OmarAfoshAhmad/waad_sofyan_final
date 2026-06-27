-- Add company_discount_amount to claims table
ALTER TABLE claims ADD COLUMN IF NOT EXISTS company_discount_amount DECIMAL(15, 2) DEFAULT 0.00;
