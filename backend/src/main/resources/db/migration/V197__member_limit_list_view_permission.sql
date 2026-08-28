-- Reading one member's ceiling and reading a page of them become separate
-- grants.
--
-- They were one. A provider needs the single read while a claim is entered --
-- the patient in front of them -- and the same grant therefore carried the
-- bulk read, which is a view of the insurer's book: a different blast radius
-- and a different volume of data. Expressing "single yes, bulk no" needs two
-- permissions; a role check would have said it in a place no administrator
-- can reach and no screen can read.
INSERT INTO rbac_permissions (code, category, display_name_ar, sensitive)
VALUES ('MEMBER_LIMIT_LIST_VIEW', 'MEMBERS',
        'عرض أرصدة السقوف في قائمة المستفيدين', false)
ON CONFLICT (code) DO NOTHING;

-- The roles that own the commercial relationship read the book.
INSERT INTO rbac_role_permissions (role_code, permission_code, granted_by)
SELECT r.code, 'MEMBER_LIMIT_LIST_VIEW', 'MIGRATION_V197'
FROM (VALUES ('SUPER_ADMIN'), ('EMPLOYER_ADMIN'),
             ('MEDICAL_REVIEWER'), ('MEDICAL_REVIEW_HEAD'), ('INSURANCE_MANAGER')) AS r(code)
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- PROVIDER_STAFF and DATA_ENTRY are deliberately absent. Both can still be
-- granted it for one user through rbac_user_permission_overrides, which is the
-- point of moving the rule onto a permission at all.
