-- V102: Add complaint column to claims table
-- Added 2026-03-08 as part of Claim Financial Calculations fix

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS complaint TEXT;

COMMENT ON COLUMN claims.complaint IS 'Patient or doctor complaint/notes about the claim at entry time.';
