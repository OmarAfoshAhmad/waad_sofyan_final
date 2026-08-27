-- Separate destructive pre-authorization commands from view/review/approval.
-- Existing per-user REVOKE overrides remain authoritative.
INSERT INTO rbac_permissions(code, category, display_name_ar, sensitive) VALUES
('PREAUTH_CANCEL', 'PREAUTHORIZATIONS', 'إلغاء موافقة مسبقة', true),
('PREAUTH_DELETE', 'PREAUTHORIZATIONS', 'حذف مسودة موافقة مسبقة', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permissions(role_code, permission_code) VALUES
('SUPER_ADMIN', 'PREAUTH_CANCEL'),
('SUPER_ADMIN', 'PREAUTH_DELETE'),
('MEDICAL_REVIEW_HEAD', 'PREAUTH_CANCEL'),
('INSURANCE_MANAGER', 'PREAUTH_CANCEL'),
('PROVIDER_STAFF', 'PREAUTH_DELETE')
ON CONFLICT (role_code, permission_code) DO NOTHING;
