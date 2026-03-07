-- Align provider_allowed_employers with ProviderAllowedEmployer entity

ALTER TABLE provider_allowed_employers
    ADD COLUMN IF NOT EXISTS active BOOLEAN,
    ADD COLUMN IF NOT EXISTS notes VARCHAR(500);

UPDATE provider_allowed_employers
SET active = true
WHERE active IS NULL;

ALTER TABLE provider_allowed_employers
    ALTER COLUMN active SET DEFAULT true;

ALTER TABLE provider_allowed_employers
    ALTER COLUMN active SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pae_active
    ON provider_allowed_employers(active);
