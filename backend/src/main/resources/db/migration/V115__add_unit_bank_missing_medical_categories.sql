-- Unit Bank benefit table needs these atomic service categories as normal
-- coverage rules, not as free-text notes folded into broader categories.

INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES
 ('CAT-ENDOSCOPY',       'المناظير',                     'المناظير',                     NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-CARDIO-CHECKUP',  'تغطيات القلب والكشف الطبي',    'تغطيات القلب والكشف الطبي',    NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-HEARING-AID',     'السماعات الطبية',              'السماعات الطبية',              NULL, NULL, NULL, false, true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    active = true,
    deleted = false,
    updated_at = NOW();

INSERT INTO medical_category_contexts(category_id, context_type, is_default, is_active)
SELECT id, 'OUTPATIENT', true, true
FROM medical_categories
WHERE code IN ('CAT-ENDOSCOPY', 'CAT-CARDIO-CHECKUP')
ON CONFLICT (category_id, context_type) DO UPDATE
SET is_active = true,
    is_default = EXCLUDED.is_default,
    updated_at = NOW();

INSERT INTO medical_category_contexts(category_id, context_type, is_default, is_active)
SELECT id, 'OUTPATIENT', true, true
FROM medical_categories
WHERE code = 'CAT-HEARING-AID'
ON CONFLICT (category_id, context_type) DO UPDATE
SET is_active = true,
    is_default = EXCLUDED.is_default,
    updated_at = NOW();
