-- Normalized RBAC foundation. Roles remain stable templates; effective access is
-- role grants plus explicit per-user GRANT/REVOKE overrides (REVOKE wins).
CREATE TABLE rbac_permissions (
    code VARCHAR(100) PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    display_name_ar VARCHAR(200) NOT NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_rbac_permission_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE TABLE rbac_role_permissions (
    role_code VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL REFERENCES rbac_permissions(code),
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(100) NOT NULL DEFAULT 'MIGRATION_V191',
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT chk_rbac_role_code CHECK (role_code IN (
        'SUPER_ADMIN','MEDICAL_REVIEWER','MEDICAL_REVIEW_HEAD','INSURANCE_MANAGER',
        'ACCOUNTANT','PROVIDER_STAFF','EMPLOYER_ADMIN','DATA_ENTRY','FINANCE_VIEWER'))
);

CREATE TABLE rbac_user_permission_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES rbac_permissions(code),
    effect VARCHAR(10) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by BIGINT NOT NULL REFERENCES users(id),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rbac_user_permission_override UNIQUE (user_id, permission_code),
    CONSTRAINT chk_rbac_permission_effect CHECK (effect IN ('GRANT','REVOKE')),
    CONSTRAINT chk_rbac_override_reason CHECK (length(trim(reason)) >= 3)
);

CREATE TABLE rbac_permission_change_audit (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(10) NOT NULL,
    target_user_id BIGINT,
    target_role_code VARCHAR(50),
    permission_code VARCHAR(100) NOT NULL,
    previous_effect VARCHAR(10),
    new_effect VARCHAR(10),
    reason VARCHAR(500) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rbac_audit_target CHECK (
        (target_type = 'USER' AND target_user_id IS NOT NULL AND target_role_code IS NULL) OR
        (target_type = 'ROLE' AND target_user_id IS NULL AND target_role_code IS NOT NULL)),
    CONSTRAINT chk_rbac_audit_effects CHECK (
        (previous_effect IS NULL OR previous_effect IN ('GRANT','REVOKE')) AND
        (new_effect IS NULL OR new_effect IN ('GRANT','REVOKE'))),
    CONSTRAINT chk_rbac_audit_reason CHECK (length(trim(reason)) >= 3)
);

CREATE OR REPLACE FUNCTION prevent_rbac_permission_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'rbac_permission_change_audit is append-only';
END;
$$;

CREATE TRIGGER trg_rbac_permission_audit_append_only
BEFORE UPDATE OR DELETE ON rbac_permission_change_audit
FOR EACH ROW EXECUTE FUNCTION prevent_rbac_permission_audit_mutation();

ALTER TABLE users ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0;

INSERT INTO rbac_permissions(code, category, display_name_ar, sensitive) VALUES
('MEMBER_VIEW','MEMBERS','عرض المستفيدين',false),('MEMBER_CREATE','MEMBERS','إضافة مستفيد',false),
('MEMBER_EDIT_IDENTITY','MEMBERS','تعديل بيانات المستفيد',true),('MEMBER_CHANGE_STATUS','MEMBERS','تغيير حالة المستفيد',true),
('MEMBER_TRANSFER_EMPLOYER','MEMBERS','نقل المستفيد أو الأسرة',true),('MEMBER_HARD_DELETE','MEMBERS','حذف المستفيد نهائياً',true),
('MEMBER_FINANCIAL_VIEW','MEMBERS','عرض الحسابات المالية للمستفيد',true),('MEMBER_IMPORT','MEMBERS','استيراد المستفيدين',true),
('MEMBER_EXPORT','MEMBERS','تصدير المستفيدين',true),('CLAIM_VIEW','CLAIMS','عرض المطالبات',false),
('CLAIM_CREATE','CLAIMS','إدخال مطالبة',false),('CLAIM_REVIEW','CLAIMS','مراجعة مطالبة',true),
('CLAIM_APPROVE','CLAIMS','اعتماد مطالبة',true),('CLAIM_REVERSE','CLAIMS','عكس أثر مطالبة',true),
('PREAUTH_VIEW','PREAUTHORIZATIONS','عرض الموافقات المسبقة',false),('PREAUTH_CREATE','PREAUTHORIZATIONS','إنشاء موافقة مسبقة',false),
('PREAUTH_REVIEW','PREAUTHORIZATIONS','مراجعة موافقة مسبقة',true),('PREAUTH_APPROVE','PREAUTHORIZATIONS','اعتماد موافقة مسبقة',true),
('PROVIDER_VIEW','PROVIDERS','عرض مقدمي الخدمة',false),('PROVIDER_MANAGE','PROVIDERS','إدارة مقدمي الخدمة',true),
('EMPLOYER_VIEW','EMPLOYERS','عرض جهات العمل',false),('EMPLOYER_MANAGE','EMPLOYERS','إدارة جهات العمل',true),
('CONTRACT_VIEW','CONTRACTS_PRICING','عرض العقود والأسعار',false),('CONTRACT_MANAGE','CONTRACTS_PRICING','إدارة العقود والأسعار',true),
('PRICE_LIST_IMPORT','CONTRACTS_PRICING','استيراد قوائم الأسعار',true),('PRICE_LIST_POST','CONTRACTS_PRICING','ترحيل قوائم الأسعار للعقود',true),
('BENEFIT_POLICY_VIEW','BENEFITS','عرض وثائق المنافع',false),('BENEFIT_POLICY_MANAGE','BENEFITS','إدارة وثائق المنافع',true),
('SETTLEMENT_VIEW','SETTLEMENTS','عرض التسويات',true),('SETTLEMENT_MANAGE','SETTLEMENTS','إدارة التسويات',true),
('FINANCIAL_REPORT_VIEW','REPORTS','عرض التقارير المالية',true),('OPERATIONAL_REPORT_VIEW','REPORTS','عرض التقارير التشغيلية',false),
('USER_VIEW','USERS_SECURITY','عرض المستخدمين',true),('USER_MANAGE','USERS_SECURITY','إدارة المستخدمين',true),
('ROLE_PERMISSION_MANAGE','USERS_SECURITY','إدارة الأدوار والصلاحيات',true),('SECURITY_AUDIT_VIEW','USERS_SECURITY','عرض سجل التدقيق الأمني',true),
('SESSION_REVOKE','USERS_SECURITY','سحب جلسات المستخدمين',true),('SYSTEM_SETTINGS_VIEW','SYSTEM','عرض إعدادات النظام',true),
('SYSTEM_SETTINGS_MANAGE','SYSTEM','إدارة إعدادات النظام',true),('DANGER_ZONE_EXECUTE','SYSTEM','تنفيذ العمليات الخطرة',true);

-- SUPER_ADMIN receives the full catalogue. Other templates are deliberately
-- conservative; individual expansion is explicit and audited.
INSERT INTO rbac_role_permissions(role_code, permission_code)
SELECT 'SUPER_ADMIN', code FROM rbac_permissions;

INSERT INTO rbac_role_permissions(role_code, permission_code) VALUES
('DATA_ENTRY','MEMBER_VIEW'),('DATA_ENTRY','MEMBER_CREATE'),('DATA_ENTRY','MEMBER_EDIT_IDENTITY'),
('DATA_ENTRY','MEMBER_IMPORT'),('DATA_ENTRY','EMPLOYER_VIEW'),('DATA_ENTRY','BENEFIT_POLICY_VIEW'),
('EMPLOYER_ADMIN','MEMBER_VIEW'),('EMPLOYER_ADMIN','CLAIM_VIEW'),('EMPLOYER_ADMIN','PREAUTH_VIEW'),
('EMPLOYER_ADMIN','OPERATIONAL_REPORT_VIEW'),('EMPLOYER_ADMIN','BENEFIT_POLICY_VIEW'),
('PROVIDER_STAFF','MEMBER_VIEW'),('PROVIDER_STAFF','CLAIM_VIEW'),('PROVIDER_STAFF','CLAIM_CREATE'),
('PROVIDER_STAFF','PREAUTH_VIEW'),('PROVIDER_STAFF','PREAUTH_CREATE'),('PROVIDER_STAFF','CONTRACT_VIEW'),
('MEDICAL_REVIEWER','MEMBER_VIEW'),('MEDICAL_REVIEWER','MEMBER_FINANCIAL_VIEW'),
('MEDICAL_REVIEWER','CLAIM_VIEW'),('MEDICAL_REVIEWER','CLAIM_REVIEW'),
('MEDICAL_REVIEWER','PREAUTH_VIEW'),('MEDICAL_REVIEWER','PREAUTH_REVIEW'),
('MEDICAL_REVIEWER','PROVIDER_VIEW'),('MEDICAL_REVIEWER','CONTRACT_VIEW'),('MEDICAL_REVIEWER','BENEFIT_POLICY_VIEW');

INSERT INTO rbac_role_permissions(role_code, permission_code)
SELECT 'MEDICAL_REVIEW_HEAD', permission_code FROM rbac_role_permissions WHERE role_code='MEDICAL_REVIEWER';
INSERT INTO rbac_role_permissions(role_code, permission_code) VALUES
('MEDICAL_REVIEW_HEAD','CLAIM_APPROVE'),('MEDICAL_REVIEW_HEAD','PREAUTH_APPROVE');

INSERT INTO rbac_role_permissions(role_code, permission_code)
SELECT 'INSURANCE_MANAGER', permission_code FROM rbac_role_permissions WHERE role_code='MEDICAL_REVIEWER';
INSERT INTO rbac_role_permissions(role_code, permission_code) VALUES
('INSURANCE_MANAGER','CLAIM_APPROVE'),('INSURANCE_MANAGER','CLAIM_REVERSE'),
('INSURANCE_MANAGER','PREAUTH_APPROVE'),('INSURANCE_MANAGER','SETTLEMENT_VIEW'),
('INSURANCE_MANAGER','FINANCIAL_REPORT_VIEW'),
('ACCOUNTANT','SETTLEMENT_VIEW'),('ACCOUNTANT','SETTLEMENT_MANAGE'),
('ACCOUNTANT','FINANCIAL_REPORT_VIEW'),('ACCOUNTANT','CLAIM_VIEW'),('ACCOUNTANT','CONTRACT_VIEW'),
('FINANCE_VIEWER','SETTLEMENT_VIEW'),('FINANCE_VIEWER','FINANCIAL_REPORT_VIEW'),
('FINANCE_VIEWER','CLAIM_VIEW'),('FINANCE_VIEWER','CONTRACT_VIEW');

CREATE INDEX idx_rbac_override_user ON rbac_user_permission_overrides(user_id);
CREATE INDEX idx_rbac_audit_target_user ON rbac_permission_change_audit(target_user_id, occurred_at DESC);
CREATE INDEX idx_rbac_audit_role ON rbac_permission_change_audit(target_role_code, occurred_at DESC);
