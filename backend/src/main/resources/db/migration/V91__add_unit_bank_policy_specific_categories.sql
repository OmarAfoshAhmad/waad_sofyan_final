-- Policy-specific clauses required by the Masraf Al Wahda benefit table.
-- These are medical-service classifications, not generic old-data aliases.
INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES
 ('CAT-CARDIAC-SURGERY', 'عمليات القلب والشرايين', 'عمليات القلب والشرايين', NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-DRUG-GENERAL', 'أدوية الصرف العام', 'أدوية الصرف العام', NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-DRUG-CHRONIC', 'أدوية الأمراض المزمنة', 'أدوية الأمراض المزمنة', NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-SPEECH-THERAPY', 'جلسات علاج النطق للأطفال حتى سن 16 عام', 'جلسات علاج النطق للأطفال حتى سن 16 عام', NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-FERTILITY-DRUG', 'أدوية أمراض الخصوبة والعقم', 'أدوية أمراض الخصوبة والعقم', NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-THERAPEUTIC-INJ', 'الحقن العلاجية', 'الحقن العلاجية', NULL, NULL, NULL, false, true, NOW(), NOW()),
 ('CAT-STIMULANT-DRUG', 'إبر منشطة وأدوية وكل ما يتعلق بها', 'إبر منشطة وأدوية وكل ما يتعلق بها', NULL, NULL, NULL, false, true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    active = true,
    deleted = false,
    updated_at = NOW();

INSERT INTO medical_category_contexts(category_id, context_type, is_default, is_active)
SELECT id, 'INPATIENT', true, true
FROM medical_categories
WHERE code = 'CAT-CARDIAC-SURGERY'
ON CONFLICT (category_id, context_type) DO UPDATE
SET is_active = true, is_default = true, updated_at = NOW();

INSERT INTO medical_category_contexts(category_id, context_type, is_default, is_active)
SELECT id, 'OUTPATIENT', true, true
FROM medical_categories
WHERE code IN (
    'CAT-DRUG-GENERAL',
    'CAT-DRUG-CHRONIC',
    'CAT-SPEECH-THERAPY',
    'CAT-FERTILITY-DRUG',
    'CAT-THERAPEUTIC-INJ',
    'CAT-STIMULANT-DRUG'
)
ON CONFLICT (category_id, context_type) DO UPDATE
SET is_active = true, is_default = true, updated_at = NOW();
