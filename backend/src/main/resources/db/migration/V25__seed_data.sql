-- ============================================================
-- V25: بيانات البذر الأساسية
-- ============================================================
-- يتضمن:
-- 1) Feature Flags
-- 2) System Settings الأساسية
-- 3) التصنيفات الطبية الهرمية (عربي)
-- 4) أسباب رفض المطالبات الافتراضية

-- ------------------------------------------------------------
-- 1) Feature Flags
-- ------------------------------------------------------------
INSERT INTO feature_flags (flag_key, flag_name, description, enabled, created_by, created_at, updated_at)
VALUES
    (
        'PROVIDER_PORTAL_ENABLED',
        'بوابة الخدمة المباشرة',
        'تفعيل بوابة إدخال المطالبات المباشرة عبر مزودي الخدمة. عند التعطيل يعمل النظام في وضع الدفعات الشهرية فقط.',
        false, 'SYSTEM', NOW(), NOW()
    ),
    (
        'DIRECT_CLAIM_SUBMISSION_ENABLED',
        'التقديم المباشر للمطالبات',
        'السماح بإنشاء مطالبات فردية مباشرة من بوابة المزود. يتطلب تفعيل PROVIDER_PORTAL_ENABLED أيضاً.',
        false, 'SYSTEM', NOW(), NOW()
    ),
    (
        'BATCH_CLAIMS_ENABLED',
        'نظام الدفعات الشهرية',
        'تفعيل إدخال المطالبات عبر الدفعات الشهرية. هذا هو المسار الأساسي الحالي لإدخال المطالبات.',
        true, 'SYSTEM', NOW(), NOW()
    )
ON CONFLICT (flag_key) DO NOTHING;

-- ------------------------------------------------------------
-- 2) System Settings
-- ------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, description, category, is_editable, default_value, validation_rules, active, created_at, updated_at)
VALUES
    ('LOGO_URL',        '',               'STRING',  'رابط شعار النظام. اتركه فارغاً للشعار الافتراضي.',                                  'UI',          true, '',                 NULL,                                           true, NOW(), NOW()),
    ('FONT_FAMILY',     'Tajawal',        'STRING',  'نوع الخط الأساسي للنظام.',                                                           'UI',          true, 'Tajawal',          'allowed:Tajawal,Cairo,Almarai,Noto Naskh Arabic', true, NOW(), NOW()),
    ('FONT_SIZE_BASE',  '14',             'INTEGER', 'حجم الخط الأساسي بالبكسل.',                                                          'UI',          true, '14',               'min:12,max:18',                                true, NOW(), NOW()),
    ('SYSTEM_NAME_AR',  'نظام واعد الطبي', 'STRING',  'اسم النظام باللغة العربية — يظهر في العنوان والتقارير.',                            'UI',          true, 'نظام واعد الطبي',  'maxlength:60',                                 true, NOW(), NOW()),
    ('SYSTEM_NAME_EN',  'TBA WAAD System','STRING',  'System name in English — appears in reports and API responses.',                    'UI',          true, 'TBA WAAD System',  'maxlength:60',                                 true, NOW(), NOW()),
    ('BENEFICIARY_NUMBER_FORMAT', 'PREFIX_SEQUENCE', 'STRING',  'صيغة ترقيم المستفيدين: PREFIX_SEQUENCE | YEAR_SEQUENCE | SEQUENTIAL.',    'MEMBERS',     true, 'PREFIX_SEQUENCE',  'allowed:PREFIX_SEQUENCE,YEAR_SEQUENCE,SEQUENTIAL', true, NOW(), NOW()),
    ('BENEFICIARY_NUMBER_PREFIX', 'MEM',             'STRING',  'البادئة في رقم المستفيد (مع PREFIX_SEQUENCE).',                           'MEMBERS',     true, 'MEM',              'maxlength:10',                                 true, NOW(), NOW()),
    ('BENEFICIARY_NUMBER_DIGITS', '6',               'INTEGER', 'عدد أرقام الجزء التسلسلي في رقم المستفيد.',                               'MEMBERS',     true, '6',                'min:4,max:10',                                 true, NOW(), NOW()),
    ('ELIGIBILITY_STRICT_MODE',       'false', 'BOOLEAN', 'الوضع الصارم: رفض تلقائي لأي طلب خارج نطاق التغطية.',                         'ELIGIBILITY', true, 'false', NULL,            true, NOW(), NOW()),
    ('WAITING_PERIOD_DAYS_DEFAULT',   '30',    'INTEGER', 'فترة الانتظار الافتراضية بالأيام عند إضافة مستفيد لوثيقة.',                   'ELIGIBILITY', true, '30',   'min:0,max:365', true, NOW(), NOW()),
    ('ELIGIBILITY_GRACE_PERIOD_DAYS', '7',     'INTEGER', 'فترة السماح بالأيام بعد انتهاء صلاحية الوثيقة.',                               'ELIGIBILITY', true, '7',    'min:0,max:30',  true, NOW(), NOW())
ON CONFLICT (setting_key) DO NOTHING;

-- ------------------------------------------------------------
-- 3) التصنيفات الطبية الهرمية (عربي)
-- ------------------------------------------------------------
INSERT INTO medical_categories (code, category_code, category_name, category_name_ar, name, name_ar, name_en, context, parent_id, active)
VALUES
    ('CAT-IP', 'CAT-IP', 'إيواء', 'إيواء', 'إيواء', 'إيواء', 'Inpatient', 'INPATIENT', NULL, true),
    ('CAT-OP', 'CAT-OP', 'عيادات خارجية', 'عيادات خارجية', 'عيادات خارجية', 'عيادات خارجية', 'Outpatient', 'OUTPATIENT', NULL, true)
ON CONFLICT (code) DO UPDATE
SET category_name = EXCLUDED.category_name,
    category_name_ar = EXCLUDED.category_name_ar,
    name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    name_en = EXCLUDED.name_en,
    context = EXCLUDED.context,
    active = true;

INSERT INTO medical_categories (code, category_code, category_name, category_name_ar, name, name_ar, name_en, context, parent_id, active)
VALUES
    ('CAT-IP-GEN',   'CAT-IP-GEN',   'عام',                    'عام',                    'عام',                    'عام',                    'General',                    'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-IP-NURSE', 'CAT-IP-NURSE', 'تمريض منزلي',            'تمريض منزلي',            'تمريض منزلي',            'تمريض منزلي',            'Home Nursing',               'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-IP-PHYSIO','CAT-IP-PHYSIO','علاج طبيعي',             'علاج طبيعي',             'علاج طبيعي',             'علاج طبيعي',             'Physiotherapy',              'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-IP-WORK',  'CAT-IP-WORK',  'إصابات عمل',             'إصابات عمل',             'إصابات عمل',             'إصابات عمل',             'Work Injuries',              'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-IP-PSYCH', 'CAT-IP-PSYCH', 'طب نفسي',                'طب نفسي',                'طب نفسي',                'طب نفسي',                'Psychiatry',                 'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-IP-MATER', 'CAT-IP-MATER', 'ولادة طبيعية وقيصرية',   'ولادة طبيعية وقيصرية',   'ولادة طبيعية وقيصرية',   'ولادة طبيعية وقيصرية',   'Maternity',                  'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-IP-COMPL', 'CAT-IP-COMPL', 'مضاعفات حمل',            'مضاعفات حمل',            'مضاعفات حمل',            'مضاعفات حمل',            'Pregnancy Complications',    'INPATIENT',  (SELECT id FROM medical_categories WHERE code = 'CAT-IP'), true),
    ('CAT-OP-GEN',   'CAT-OP-GEN',   'عام',                     'عام',                     'عام',                     'عام',                     'General',                    'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-RAD',   'CAT-OP-RAD',   'أشعة تحاليل رسوم أطباء', 'أشعة تحاليل رسوم أطباء', 'أشعة تحاليل رسوم أطباء', 'أشعة تحاليل رسوم أطباء', 'Radiology and Labs',         'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-MRI',   'CAT-OP-MRI',   'رنين مغناطيسي',          'رنين مغناطيسي',          'رنين مغناطيسي',          'رنين مغناطيسي',          'MRI',                        'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-DRUG',  'CAT-OP-DRUG',  'علاجات وأدوية روتينية',  'علاجات وأدوية روتينية',  'علاجات وأدوية روتينية',  'علاجات وأدوية روتينية',  'Routine Drugs and Treatments','OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-EQUIP', 'CAT-OP-EQUIP', 'أجهزة ومعدات',           'أجهزة ومعدات',           'أجهزة ومعدات',           'أجهزة ومعدات',           'Equipment and Devices',      'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-PHYSIO','CAT-OP-PHYSIO','علاج طبيعي',             'علاج طبيعي',             'علاج طبيعي',             'علاج طبيعي',             'Physiotherapy',              'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-DENT-R','CAT-OP-DENT-R','أسنان روتيني',           'أسنان روتيني',           'أسنان روتيني',           'أسنان روتيني',           'Routine Dentistry',          'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-DENT-C','CAT-OP-DENT-C','أسنان تجميلي',           'أسنان تجميلي',           'أسنان تجميلي',           'أسنان تجميلي',           'Cosmetic Dentistry',         'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true),
    ('CAT-OP-GLASS', 'CAT-OP-GLASS', 'النظارة الطبية',          'النظارة الطبية',          'النظارة الطبية',          'النظارة الطبية',          'Medical Glasses',            'OUTPATIENT', (SELECT id FROM medical_categories WHERE code = 'CAT-OP'), true)
ON CONFLICT (code) DO UPDATE
SET category_name = EXCLUDED.category_name,
    category_name_ar = EXCLUDED.category_name_ar,
    name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    name_en = EXCLUDED.name_en,
    context = EXCLUDED.context,
    parent_id = EXCLUDED.parent_id,
    active = true;

DELETE FROM medical_category_roots;

INSERT INTO medical_category_roots (category_id, root_id)
SELECT id, id FROM medical_categories WHERE parent_id IS NULL AND active = true
ON CONFLICT DO NOTHING;

INSERT INTO medical_category_roots (category_id, root_id)
SELECT c.id, c.parent_id
FROM medical_categories c
WHERE c.parent_id IS NOT NULL AND c.active = true
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
-- 4) أسباب رفض المطالبات الافتراضية
-- ------------------------------------------------------------
INSERT INTO claim_rejection_reasons (reason_text) VALUES
    ('تجاوز السعر المتفق عليه'),
    ('الخدمة غير مغطاة'),
    ('المستفيد استهلك رصيده')
ON CONFLICT DO NOTHING;
