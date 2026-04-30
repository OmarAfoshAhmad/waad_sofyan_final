-- Create claim drafts table for single-draft-per-user-per-batch autosave
CREATE TABLE IF NOT EXISTS claim_drafts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    batch_id BIGINT NOT NULL REFERENCES claim_batches(id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    data_json JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_claim_drafts_user_batch UNIQUE (user_id, batch_id)
);

CREATE INDEX IF NOT EXISTS idx_claim_drafts_batch ON claim_drafts(batch_id);
CREATE INDEX IF NOT EXISTS idx_claim_drafts_updated_at ON claim_drafts(updated_at DESC);

CREATE OR REPLACE FUNCTION set_claim_drafts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_claim_drafts_updated_at ON claim_drafts;
CREATE TRIGGER trg_claim_drafts_updated_at
BEFORE UPDATE ON claim_drafts
FOR EACH ROW
EXECUTE FUNCTION set_claim_drafts_updated_at();
