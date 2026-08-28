-- Align persisted role templates with the operations that were intentionally
-- available before endpoint guards moved from role names to capabilities.
-- Existing per-user DENY overrides remain authoritative and are not changed.
INSERT INTO rbac_permissions(code, category, display_name_ar, sensitive) VALUES
('MEMBER_LIMIT_VIEW', 'MEMBERS', 'عرض رصيد سقف المستفيد للتعامل الطبي', false),
('MEMBER_REINSTATE_TERMINATED', 'MEMBERS', 'إعادة عضوية منتهية استثنائياً', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permissions(role_code, permission_code) VALUES
('SUPER_ADMIN', 'MEMBER_LIMIT_VIEW'),
('SUPER_ADMIN', 'MEMBER_REINSTATE_TERMINATED'),
('DATA_ENTRY', 'MEMBER_LIMIT_VIEW'),
('EMPLOYER_ADMIN', 'MEMBER_CREATE'),
('EMPLOYER_ADMIN', 'MEMBER_EDIT_IDENTITY'),
('EMPLOYER_ADMIN', 'MEMBER_CHANGE_STATUS'),
('EMPLOYER_ADMIN', 'MEMBER_TRANSFER_EMPLOYER'),
('EMPLOYER_ADMIN', 'MEMBER_EXPORT'),
('EMPLOYER_ADMIN', 'MEMBER_LIMIT_VIEW'),
('EMPLOYER_ADMIN', 'EMPLOYER_VIEW'),
('PROVIDER_STAFF', 'EMPLOYER_VIEW'),
('PROVIDER_STAFF', 'MEMBER_LIMIT_VIEW'),
('MEDICAL_REVIEWER', 'MEMBER_LIMIT_VIEW'),
('MEDICAL_REVIEW_HEAD', 'MEMBER_LIMIT_VIEW'),
('INSURANCE_MANAGER', 'MEMBER_LIMIT_VIEW'),
('MEDICAL_REVIEWER', 'EMPLOYER_VIEW'),
('MEDICAL_REVIEW_HEAD', 'EMPLOYER_VIEW'),
('INSURANCE_MANAGER', 'EMPLOYER_VIEW'),
('ACCOUNTANT', 'EMPLOYER_VIEW'),
('FINANCE_VIEWER', 'EMPLOYER_VIEW')
ON CONFLICT (role_code, permission_code) DO NOTHING;
