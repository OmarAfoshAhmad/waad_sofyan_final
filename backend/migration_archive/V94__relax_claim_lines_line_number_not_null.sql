-- Align claim_lines schema with current ClaimLine entity (no lineNumber field mapped)

ALTER TABLE claim_lines
    ALTER COLUMN line_number DROP NOT NULL;
