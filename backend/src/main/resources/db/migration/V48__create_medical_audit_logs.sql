CREATE TABLE IF NOT EXISTS medical_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(100) NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason TEXT NULL,
    before_state JSONB NULL,
    after_state JSONB NULL,
    correlation_id VARCHAR(100) NOT NULL,
    source VARCHAR(20) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_med_audit_entity_id
    ON medical_audit_logs(entity_id);

CREATE INDEX IF NOT EXISTS idx_med_audit_timestamp
    ON medical_audit_logs(event_timestamp);

CREATE INDEX IF NOT EXISTS idx_med_audit_correlation
    ON medical_audit_logs(correlation_id);

CREATE OR REPLACE FUNCTION prevent_medical_audit_logs_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'medical_audit_logs is immutable: operation % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_update_medical_audit_logs ON medical_audit_logs;
CREATE TRIGGER trg_no_update_medical_audit_logs
BEFORE UPDATE ON medical_audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_medical_audit_logs_mutation();

DROP TRIGGER IF EXISTS trg_no_delete_medical_audit_logs ON medical_audit_logs;
CREATE TRIGGER trg_no_delete_medical_audit_logs
BEFORE DELETE ON medical_audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_medical_audit_logs_mutation();
