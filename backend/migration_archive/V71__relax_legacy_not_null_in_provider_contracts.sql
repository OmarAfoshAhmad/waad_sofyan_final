-- V71: Relax legacy NOT NULL constraint blocking modern provider contract creation
-- Root cause: ModernProviderContract entity no longer uses employer_id, but legacy schema keeps it NOT NULL.

ALTER TABLE provider_contracts
    ALTER COLUMN employer_id DROP NOT NULL;
