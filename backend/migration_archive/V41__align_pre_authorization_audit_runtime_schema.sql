-- V41: Create pre_authorization_audit table required by PreAuthorizationAudit entity

CREATE TABLE IF NOT EXISTS pre_authorization_audit (
    id BIGSERIAL PRIMARY KEY,
    pre_authorization_id BIGINT NOT NULL,
    reference_number VARCHAR(50),
    changed_by VARCHAR(100) NOT NULL,
    change_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action VARCHAR(20) NOT NULL,
    field_name VARCHAR(50),
    old_value TEXT,
    new_value TEXT,
    notes VARCHAR(500),
    ip_address VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_audit_preauth ON pre_authorization_audit(pre_authorization_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON pre_authorization_audit(changed_by);
CREATE INDEX IF NOT EXISTS idx_audit_date ON pre_authorization_audit(change_date);
CREATE INDEX IF NOT EXISTS idx_audit_action ON pre_authorization_audit(action);
