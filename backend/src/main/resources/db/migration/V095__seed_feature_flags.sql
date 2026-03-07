-- ============================================================
-- V095: Seed data — feature flags + configurable system settings
-- ============================================================
-- Depends on: V015 (system_settings, feature_flags tables)

-- ----------------------------------------------------------
-- SECTION 1: Feature flags — claim entry mode toggles
-- ----------------------------------------------------------
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

-- ----------------------------------------------------------
-- SECTION 2: System settings — UI / appearance
-- ----------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, description, category, is_editable, default_value, validation_rules, active, created_at, updated_at)
VALUES
    ('LOGO_URL',        '',              'STRING',  'رابط شعار النظام. اتركه فارغاً للشعار الافتراضي.',                              'UI',          true, '',               NULL,                                           true, NOW(), NOW()),
    ('FONT_FAMILY',     'Tajawal',       'STRING',  'نوع الخط الأساسي للنظام.',                                                       'UI',          true, 'Tajawal',        'allowed:Tajawal,Cairo,Almarai,Noto Naskh Arabic', true, NOW(), NOW()),
    ('FONT_SIZE_BASE',  '14',            'INTEGER', 'حجم الخط الأساسي بالبكسل.',                                                      'UI',          true, '14',             'min:12,max:18',                                true, NOW(), NOW()),
    ('SYSTEM_NAME_AR',  'نظام واعد الطبي','STRING', 'اسم النظام باللغة العربية — يظهر في العنوان والتقارير.',                         'UI',          true, 'نظام واعد الطبي','maxlength:60',                                 true, NOW(), NOW()),
    ('SYSTEM_NAME_EN',  'TBA WAAD System','STRING', 'System name in English — appears in reports and API responses.',                  'UI',          true, 'TBA WAAD System','maxlength:60',                                 true, NOW(), NOW()),
    -- Member numbering
    ('BENEFICIARY_NUMBER_FORMAT',  'PREFIX_SEQUENCE','STRING',  'صيغة ترقيم المستفيدين: PREFIX_SEQUENCE | YEAR_SEQUENCE | SEQUENTIAL.', 'MEMBERS', true, 'PREFIX_SEQUENCE','allowed:PREFIX_SEQUENCE,YEAR_SEQUENCE,SEQUENTIAL',true, NOW(), NOW()),
    ('BENEFICIARY_NUMBER_PREFIX',  'MEM',           'STRING',  'البادئة في رقم المستفيد (مع PREFIX_SEQUENCE).',                          'MEMBERS', true, 'MEM',            'maxlength:10',                                 true, NOW(), NOW()),
    ('BENEFICIARY_NUMBER_DIGITS',  '6',             'INTEGER', 'عدد أرقام الجزء التسلسلي في رقم المستفيد.',                              'MEMBERS', true, '6',              'min:4,max:10',                                 true, NOW(), NOW()),
    -- Eligibility rules
    ('ELIGIBILITY_STRICT_MODE',       'false', 'BOOLEAN', 'الوضع الصارم: رفض تلقائي لأي طلب خارج نطاق التغطية.',                       'ELIGIBILITY', true, 'false', NULL,            true, NOW(), NOW()),
    ('WAITING_PERIOD_DAYS_DEFAULT',   '30',    'INTEGER', 'فترة الانتظار الافتراضية بالأيام عند إضافة مستفيد لوثيقة.',                 'ELIGIBILITY', true, '30',   'min:0,max:365', true, NOW(), NOW()),
    ('ELIGIBILITY_GRACE_PERIOD_DAYS', '7',     'INTEGER', 'فترة السماح بالأيام بعد انتهاء صلاحية الوثيقة.',                            'ELIGIBILITY', true, '7',    'min:0,max:30',  true, NOW(), NOW())
ON CONFLICT (setting_key) DO NOTHING;
