ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS review_paused BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS review_pause_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS review_paused_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_paused_by VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_claims_review_paused
    ON claims (status, review_paused)
    WHERE active = TRUE AND review_paused = TRUE;
