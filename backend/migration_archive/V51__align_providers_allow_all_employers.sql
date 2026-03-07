-- Align providers table with Provider.allowAllEmployers field

ALTER TABLE providers
    ADD COLUMN IF NOT EXISTS allow_all_employers BOOLEAN;

UPDATE providers
SET allow_all_employers = false
WHERE allow_all_employers IS NULL;

ALTER TABLE providers
    ALTER COLUMN allow_all_employers SET DEFAULT false;

ALTER TABLE providers
    ALTER COLUMN allow_all_employers SET NOT NULL;
