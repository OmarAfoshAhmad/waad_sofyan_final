-- Production audit logs must be append-only. V54 temporarily allowed admin API
-- deletion for development cleanup, but the canonical audit trail should not be
-- mutable or deletable through normal application paths.
CREATE OR REPLACE FUNCTION prevent_medical_audit_logs_mutation()
RETURNS trigger AS $$
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
