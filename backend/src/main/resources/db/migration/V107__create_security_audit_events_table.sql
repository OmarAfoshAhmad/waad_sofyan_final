-- Create unified security audit events table
CREATE TABLE IF NOT EXISTS security_audit_events (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT,
    actor_username VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    target_identifier VARCHAR(255),
    request_ip VARCHAR(45),
    user_agent TEXT,
    result VARCHAR(20) NOT NULL,
    safe_reason TEXT,
    before_state JSONB,
    after_state JSONB,
    correlation_id VARCHAR(36) NOT NULL UNIQUE,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES "user"(id) ON DELETE SET NULL
);

-- Create indexes for efficient querying
CREATE INDEX idx_audit_actor ON security_audit_events(actor_id);
CREATE INDEX idx_audit_action ON security_audit_events(action_type);
CREATE INDEX idx_audit_timestamp ON security_audit_events(event_timestamp);
CREATE INDEX idx_audit_correlation ON security_audit_events(correlation_id);
CREATE INDEX idx_audit_result ON security_audit_events(result);

-- Add comment
COMMENT ON TABLE security_audit_events IS 'Unified security and audit logging for all sensitive operations';
