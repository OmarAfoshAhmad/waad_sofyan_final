-- Align providers table with Provider.taxNumber mapping

ALTER TABLE providers
    ADD COLUMN IF NOT EXISTS tax_number VARCHAR(50);
