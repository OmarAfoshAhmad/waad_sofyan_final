-- Activating a policy known to carry a rule that will never apply (a
-- claim_context_code that is missing or disabled -- see
-- BenefitPolicyGapAuditService) is a deliberate financial decision, not a
-- convenience toggle. It gets its own permission, distinct from
-- BenefitPolicyController's ordinary hasRole('SUPER_ADMIN') activation, so
-- it can be granted (or later withheld) independently of blanket
-- super-admin access -- mirroring the same reasoning V215 already applied
-- to PROVIDER_STANDARD_SERVICES_MANAGE.
INSERT INTO rbac_permissions (code, category, display_name_ar, sensitive)
VALUES ('BENEFIT_POLICY_ACTIVATE_WITH_GAPS', 'BENEFIT_POLICIES',
        'تفعيل وثيقة تغطية رغم فجوات حرجة في القواعد', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permissions (role_code, permission_code, granted_by)
VALUES ('SUPER_ADMIN', 'BENEFIT_POLICY_ACTIVATE_WITH_GAPS', 'MIGRATION_V221')
ON CONFLICT (role_code, permission_code) DO NOTHING;
