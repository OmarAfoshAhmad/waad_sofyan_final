-- The canonical operational audit trail is medical_audit_logs.
-- The legacy systemadmin audit_logs table was replaced by a compatibility
-- facade that writes to medical_audit_logs, so keeping this table creates
-- two competing audit histories.
DROP TABLE IF EXISTS audit_logs;
