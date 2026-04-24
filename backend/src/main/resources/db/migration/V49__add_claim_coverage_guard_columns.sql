ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS pending_recalculation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS coverage_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_claims_pending_recalculation
    ON claims(pending_recalculation)
    WHERE pending_recalculation = TRUE;
