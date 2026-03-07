-- Relax financial constraints to allow 0.00 for unit prices and amounts
-- This is needed for backlog imports, rejected lines, and free follow-up services.

DO $$ 
BEGIN
    -- 1. claim_lines (unit_price > 0)
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'claim_lines_unit_price_check') THEN
        ALTER TABLE claim_lines DROP CONSTRAINT claim_lines_unit_price_check;
    END IF;
    ALTER TABLE claim_lines ADD CONSTRAINT claim_lines_unit_price_check CHECK (unit_price >= 0);

    -- 2. claim_lines (total_amount > 0)
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'claim_lines_total_amount_check') THEN
        ALTER TABLE claim_lines DROP CONSTRAINT claim_lines_total_amount_check;
    END IF;
    ALTER TABLE claim_lines ADD CONSTRAINT claim_lines_total_amount_check CHECK (total_amount >= 0);

    -- 3. claims (requested_amount > 0)
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'claims_requested_amount_check') THEN
        ALTER TABLE claims DROP CONSTRAINT claims_requested_amount_check;
    END IF;
    ALTER TABLE claims ADD CONSTRAINT claims_requested_amount_check CHECK (requested_amount >= 0);

END $$;
