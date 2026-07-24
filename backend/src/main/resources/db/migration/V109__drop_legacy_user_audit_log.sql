-- ============================================================
-- V109: Drop legacy user_audit_log table
-- ============================================================
-- Superseded by security_audit_events (V107), which is the single
-- source of truth for security-relevant events (login, lockout,
-- password change/reset, email verification, user CRUD, role changes).
--
-- Every write path into user_audit_log was redirected to
-- security_audit_events on 2026-07-24 (LoginSecurityService,
-- UserSecurityService, PasswordManagementService,
-- EmailVerificationService). No code references UserAuditLog or
-- UserAuditLogRepository anymore. The database is experimental/
-- pre-production, so the 484 historical rows are not migrated forward.
--
-- Not affected: user_login_attempts (still actively written by
-- LoginSecurityService for lockout tracking) and audit_logs /
-- medical_audit_logs (separate business/clinical audit domains,
-- deliberately kept out of this consolidation).

DROP TABLE IF EXISTS user_audit_log;
