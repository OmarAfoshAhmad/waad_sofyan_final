-- V42: Add soft-delete audit fields to claims table
-- deleted_at: timestamp when the claim was soft-deleted
-- deleted_by: email/username of who performed the deletion

ALTER TABLE claims ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE claims ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

-- Index for efficient listing of deleted claims
CREATE INDEX IF NOT EXISTS idx_claims_active_deleted ON claims (active, deleted_at DESC);
