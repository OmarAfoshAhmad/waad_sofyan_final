ALTER TABLE claims ADD COLUMN IF NOT EXISTS reviewed_by_id BIGINT;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_claims_reviewed_by_id ON claims(reviewed_by_id);
