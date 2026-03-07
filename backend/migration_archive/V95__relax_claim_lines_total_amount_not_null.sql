-- Align claim_lines schema with current ClaimLine entity using total_price

ALTER TABLE claim_lines
    ALTER COLUMN total_amount DROP NOT NULL;
