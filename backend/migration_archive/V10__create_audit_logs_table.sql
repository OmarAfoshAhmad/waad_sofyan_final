-- ============================================================================
-- V10: Create audit_logs table for system administration audit trail
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT,
    username VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    details TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_timestamp ON audit_logs(timestamp);

-- ============================================================================
-- Migration Complete: V10
-- ============================================================================
