-- Align runtime with domain model: claim_number is legacy and generated in response layer
-- Keep column, but allow NULL to prevent insert failures in claim creation flow

ALTER TABLE claims
    ALTER COLUMN claim_number DROP NOT NULL;

-- Backfill existing rows when possible (best-effort)
UPDATE claims
SET claim_number = 'CLM-' || id
WHERE claim_number IS NULL
  AND id IS NOT NULL;
