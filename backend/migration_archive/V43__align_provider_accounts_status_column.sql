-- Align provider_accounts with ProviderAccount.status enum mapping
-- Entity expects: status VARCHAR(20) NOT NULL

ALTER TABLE provider_accounts
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE provider_accounts
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE provider_accounts
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE provider_accounts
    ALTER COLUMN status SET NOT NULL;
