-- ============================================================================
-- V88: Seed 8 Root Medical Categories + Medical Specialties
-- ============================================================================
-- Purpose  : Populate the unified 8-root category taxonomy and seed the
--            first set of specialties extracted from the canonical Excel data.
-- Rules    : ON CONFLICT DO NOTHING on all inserts — fully idempotent.
--            No DELETE, no TRUNCATE, no DROP.
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION 1: 8 ROOT MEDICAL CATEGORIES
-- ─────────────────────────────────────────────────────────────────────────────
-- These are the only root classifications in the unified dictionary.
-- All seeded services will link to one of these via medical_service_categories.

INSERT INTO medical_categories (code, name, name_ar, name_en, deleted)
VALUES
    ('CAT-OPER',      'عمليات',         'عمليات',                'Surgeries',             FALSE),
    ('CAT-INPAT',     'إيواء',          'إيواء',                 'Inpatient',             FALSE),
    ('CAT-OUTPAT',    'عيادات خارجية',  'عيادات خارجية',          'Outpatient',            FALSE),
    ('CAT-LAB',       'تحاليل طبية',   'تحاليل طبية',            'Laboratory',            FALSE),
    ('CAT-DENT-PREV', 'اسنان وقائي',   'اسنان وقائي',            'Preventive Dentistry',  FALSE),
    ('CAT-DENT-COS',  'اسنان تجميلي',  'اسنان تجميلي',           'Cosmetic Dentistry',    FALSE),
    ('CAT-RAD',       'اشعة',          'اشعة',                  'Radiology',             FALSE),
    ('CAT-PHYSIO',    'علاج طبيعي',    'علاج طبيعي',             'Physiotherapy',         FALSE)
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- SECTION 2: MEDICAL SPECIALTIES
-- ─────────────────────────────────────────────────────────────────────────────
-- Extracted from the canonical Excel source (WE-xxx service codes).
-- No hardcoded ID references — all cross-references use code-based lookups.

INSERT INTO medical_specialties (code, name_ar, name_en, deleted)
VALUES
    -- Inpatient / ICU / Critical Care
    ('SP-ICU',          'خدمات الرعاية بالعناية المركزه',      'ICU Services',                    FALSE),
    ('SP-EMERG',        'الخدمات بأقسام الإيواء والطوارئ',      'Emergency & Inpatient Services',  FALSE),
    ('SP-NURSING',      'خدمات الرعاية الطبية',                 'General Medical Care',            FALSE),

    -- Surgery specialties
    ('SP-GEN-SURG',     'الجراحة العامة',                       'General Surgery',                 FALSE),
    ('SP-ORTHO',        'جراحة العظام',                         'Orthopedic Surgery',              FALSE),
    ('SP-NEURO-SURG',   'جراحة المخ والأعصاب',                  'Neurosurgery',                    FALSE),
    ('SP-CARDIO-SURG',  'جراحة القلب والصدر',                   'Cardiothoracic Surgery',          FALSE),
    ('SP-VASC',         'جراحة الأوعية الدموية',                'Vascular Surgery',                FALSE),
    ('SP-UROL',         'جراحة المسالك البولية',                'Urology',                         FALSE),
    ('SP-PEDS-SURG',    'جراحة الأطفال',                        'Pediatric Surgery',               FALSE),
    ('SP-PLAST',        'جراحة التجميل والحروق',                 'Plastic & Burns Surgery',         FALSE),
    ('SP-ENT-SURG',     'جراحة الأنف والأذن والحنجرة',          'ENT Surgery',                     FALSE),
    ('SP-OPHTH-SURG',   'جراحة العيون',                         'Ophthalmic Surgery',              FALSE),
    ('SP-GI-SURG',      'جراحة الجهاز الهضمي',                  'GI Surgery',                      FALSE),
    ('SP-MAXFAC',       'جراحة الوجه والفكين',                   'Maxillofacial Surgery',           FALSE),
    ('SP-OBS-SURG',     'جراحة النساء والولادة',                 'Obstetric Surgery',               FALSE),

    -- Medical specialties (outpatient / consultation)
    ('SP-CARDIO',       'أمراض القلب',                          'Cardiology',                      FALSE),
    ('SP-NEUROL',       'الأعصاب',                              'Neurology',                       FALSE),
    ('SP-GASTRO',       'الجهاز الهضمي',                        'Gastroenterology',                FALSE),
    ('SP-PULM',         'أمراض الصدر والجهاز التنفسي',          'Pulmonology',                     FALSE),
    ('SP-NEPHRO',       'أمراض الكلى',                          'Nephrology',                      FALSE),
    ('SP-ENDO',         'الغدد الصماء',                         'Endocrinology',                   FALSE),
    ('SP-RHEUM',        'الروماتولوجيا',                        'Rheumatology',                    FALSE),
    ('SP-DERMA',        'الجلدية',                              'Dermatology',                     FALSE),
    ('SP-ENT',          'الأنف والأذن والحنجرة',                 'ENT',                             FALSE),
    ('SP-OPHTH',        'أمراض العيون',                         'Ophthalmology',                   FALSE),
    ('SP-OBS-GYN',      'النساء والولادة',                       'Obstetrics & Gynecology',         FALSE),
    ('SP-PEDS',         'طب الأطفال',                            'Pediatrics',                      FALSE),
    ('SP-ONCOL',        'الأورام',                               'Oncology',                        FALSE),
    ('SP-PSYCH',        'الطب النفسي',                           'Psychiatry',                      FALSE),
    ('SP-REPRO',        'العقم والخصوبة',                        'Reproductive Medicine',           FALSE),
    ('SP-INFECT',       'الأمراض المعدية',                       'Infectious Diseases',             FALSE),

    -- Diagnostic & support
    ('SP-LAB',          'المختبر والتحاليل الطبية',              'Laboratory & Medical Tests',      FALSE),
    ('SP-RAD',          'الأشعة والتصوير الطبي',                 'Radiology & Medical Imaging',     FALSE),
    ('SP-PATH',         'علم الأنسجة',                           'Histopathology',                  FALSE),
    ('SP-CARD-DIAG',    'التشخيص القلبي',                        'Cardiac Diagnostics',             FALSE),
    ('SP-NEURO-DIAG',   'التشخيص العصبي',                        'Neurological Diagnostics',        FALSE),
    ('SP-AUDIO',        'السمع والتوازن',                         'Audiology',                       FALSE),

    -- Therapy & rehabilitation
    ('SP-PHYSIO',       'العلاج الطبيعي',                        'Physiotherapy',                   FALSE),
    ('SP-CHEMO',        'العلاج الكيماوي',                       'Chemotherapy',                    FALSE),
    ('SP-DIALYSIS',     'غسيل الكلى',                            'Dialysis',                        FALSE),
    ('SP-ANES',         'التخدير',                               'Anesthesia',                      FALSE),

    -- Dental
    ('SP-DENT',         'طب الأسنان',                             'Dentistry',                      FALSE),

    -- Accommodation
    ('SP-ACCOMM',       'خدمات الإيواء',                         'Accommodation Services',          FALSE)

ON CONFLICT (code) DO NOTHING;
