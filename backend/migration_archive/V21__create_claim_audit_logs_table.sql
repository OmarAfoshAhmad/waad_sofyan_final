-- V21: Create claim_audit_logs table required by ClaimAuditLog entity

CREATE TABLE IF NOT EXISTS claim_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    previous_requested_amount NUMERIC(15,2),
    new_requested_amount NUMERIC(15,2),
    previous_approved_amount NUMERIC(15,2),
    new_approved_amount NUMERIC(15,2),
    actor_user_id BIGINT NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comment TEXT,
    ip_address VARCHAR(45),
    before_snapshot TEXT,
    after_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_claim_audit_claim_id ON claim_audit_logs(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_audit_timestamp ON claim_audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_claim_audit_actor ON claim_audit_logs(actor_user_id);
