-- Enhancement: ClaimLine Financial Integrity
-- Adds requested vs approved amounts to preserve audit trail
-- Adds detailed rejection reasons

ALTER TABLE claim_lines
ADD COLUMN requested_unit_price DECIMAL(15, 2),
ADD COLUMN approved_unit_price DECIMAL(15, 2),
ADD COLUMN requested_quantity INTEGER,
ADD COLUMN approved_quantity INTEGER,
ADD COLUMN rejection_reason_code VARCHAR(50),
ADD COLUMN reviewer_notes TEXT;

-- INITIAL DATA POPULATION: Set sensible defaults from existing columns
UPDATE claim_lines
SET requested_unit_price = unit_price,
    approved_unit_price = CASE WHEN rejected = true THEN 0 ELSE unit_price END,
    requested_quantity = quantity,
    approved_quantity = CASE WHEN rejected = true THEN 0 ELSE quantity END;
