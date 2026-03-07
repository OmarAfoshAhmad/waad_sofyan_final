-- V098: Add financial, contract, and capacity fields to employers table
-- Addresses:
--   - CR Number (رقم السجل التجاري) for financial reports
--   - Tax Number (الرقم الضريبي) for invoicing
--   - Contact phone / email (if not already present)
--   - Contract start/end dates
--   - Max member limit

-- Commercial Registration Number
ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS cr_number VARCHAR(50);

-- Tax / VAT Number
ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS tax_number VARCHAR(50);

-- Contract period with this employer
ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS contract_start_date DATE;

ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS contract_end_date DATE;

-- Maximum members allowed (NULL = unlimited)
ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS max_member_limit INTEGER;

-- Contact fields (guard with IF NOT EXISTS in case they were added manually)
ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30);

ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS email VARCHAR(150);

ALTER TABLE employers
    ADD COLUMN IF NOT EXISTS address VARCHAR(255);
