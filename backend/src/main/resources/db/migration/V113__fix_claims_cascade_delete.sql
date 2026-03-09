-- Migration to change foreign key constraints on claims to ON DELETE CASCADE
-- This ensures that deleting a claim automatically removes its audit logs and batch item references,
-- which in turn ensures that member benefit limits (calculated from claim lines) are correctly updated.

-- 1. DROP and RE-CREATE fk_claim_audit_claim as CASCADE
ALTER TABLE claim_audit_logs 
DROP CONSTRAINT IF EXISTS fk_claim_audit_claim;

ALTER TABLE claim_audit_logs 
ADD CONSTRAINT fk_claim_audit_claim 
FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE;

-- 2. DROP and RE-CREATE fk_batch_item_claim as CASCADE
ALTER TABLE settlement_batch_items 
DROP CONSTRAINT IF EXISTS fk_batch_item_claim;

ALTER TABLE settlement_batch_items 
ADD CONSTRAINT fk_batch_item_claim 
FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE;
