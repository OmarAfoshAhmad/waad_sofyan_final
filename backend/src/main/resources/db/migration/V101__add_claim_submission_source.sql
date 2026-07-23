ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS submission_source VARCHAR(30) NOT NULL DEFAULT 'INTERNAL_DIRECT';

CREATE INDEX IF NOT EXISTS idx_claims_submission_source_status
    ON claims (submission_source, status)
    WHERE active = TRUE;
