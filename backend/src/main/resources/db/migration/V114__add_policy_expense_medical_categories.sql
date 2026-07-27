-- Add policy-expense categories that are managed through normal coverage rules.
-- These are intentionally CAT-* entries, not BEN-* special definitions, because
-- benefit tables use them as claimable IP/OP coverage lines with percentages,
-- copay, and optional limits.

INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES
 ('CAT-WORK-INJURY',   'تكلفة إصابات العمل',                         'تكلفة إصابات العمل',                         NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-MED-EVAC',      'الإخلاء الطبي',                               'الإخلاء الطبي',                               NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-EVAC-COMPANION','تكلفة شخص مرافق واحد للشخص الذي تم إخلاؤه',    'تكلفة شخص مرافق واحد للشخص الذي تم إخلاؤه',    NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-FAMILY-TRAVEL', 'تكلفة السفر لأحد أفراد العائلة المؤمن عليها', 'تكلفة السفر لأحد أفراد العائلة المؤمن عليها', NULL, NULL, NULL, false, true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    active = true,
    deleted = false,
    updated_at = NOW();

INSERT INTO medical_category_contexts(category_id, context_type, is_default, is_active)
SELECT id, 'INPATIENT', true, true
FROM medical_categories
WHERE code IN ('CAT-WORK-INJURY', 'CAT-MED-EVAC', 'CAT-EVAC-COMPANION', 'CAT-FAMILY-TRAVEL')
ON CONFLICT (category_id, context_type) DO UPDATE
SET is_active = true,
    is_default = EXCLUDED.is_default,
    updated_at = NOW();
