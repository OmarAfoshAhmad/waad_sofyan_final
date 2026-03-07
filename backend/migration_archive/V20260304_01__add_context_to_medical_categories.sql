-- ============================================================================
-- V111: Add clinical context to medical_categories
-- ============================================================================
-- Purpose: Allow each category to declare its care setting (INPATIENT,
--          OUTPATIENT, OPERATING_ROOM, EMERGENCY, SPECIAL, ANY).
--
-- Safety guarantees:
--   ✔ Column is nullable with a safe DEFAULT ('ANY') — no existing row breaks.
--   ✔ No DELETE, no DROP, no structural change to other tables.
--   ✔ Seed block uses ON CONFLICT DO NOTHING — idempotent.
--   ✔ Rollback: ALTER TABLE medical_categories DROP COLUMN IF EXISTS context;
-- ============================================================================

-- STEP 1 ─ Add the column (no-op if already present)
ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS context VARCHAR(20) NOT NULL DEFAULT 'ANY';

-- STEP 2 ─ Back-fill sensible defaults for well-known category codes
--          (only updates rows that still carry the generic default)
UPDATE medical_categories SET context = 'INPATIENT'     WHERE code IN ('CAT-INPAT')      AND context = 'ANY';
UPDATE medical_categories SET context = 'OUTPATIENT'    WHERE code IN ('CAT-OUTPAT')     AND context = 'ANY';
UPDATE medical_categories SET context = 'OPERATING_ROOM'WHERE code IN ('CAT-OPER')       AND context = 'ANY';
UPDATE medical_categories SET context = 'EMERGENCY'     WHERE code IN ('CAT-EMERG')      AND context = 'ANY';
UPDATE medical_categories SET context = 'SPECIAL'       WHERE code IN ('CAT-SPECIAL')    AND context = 'ANY';

-- STEP 3 ─ Seed new, context-tagged categories for common clinical domains
--          (covers the benefit-table categories from جدول_منافع_مصلحة_الجمارك)
INSERT INTO medical_categories (code, name, name_ar, name_en, context, active, created_at, updated_at)
VALUES
  -- Inpatient surgical specialties
  ('CAT-IP-GS',   'جراحة عامة - إيواء',          'جراحة عامة - إيواء',          'General Surgery (IP)',         'INPATIENT',      true, NOW(), NOW()),
  ('CAT-IP-VASC', 'أوعية دموية - إيواء',          'أوعية دموية - إيواء',          'Vascular Surgery (IP)',        'INPATIENT',      true, NOW(), NOW()),
  ('CAT-IP-CARD', 'قلب وقسطرة - إيواء',           'قلب وقسطرة - إيواء',           'Cardiology & Cath (IP)',       'INPATIENT',      true, NOW(), NOW()),
  ('CAT-IP-OB',   'نساء وولادة - إيواء',           'نساء وولادة - إيواء',           'Obstetrics (IP)',              'INPATIENT',      true, NOW(), NOW()),
  ('CAT-IP-ONCO', 'علاج أورام - إيواء',            'علاج أورام - إيواء',            'Oncology (IP)',                'INPATIENT',      true, NOW(), NOW()),
  ('CAT-IP-DIAL', 'غسيل كلى - إيواء',             'غسيل كلى - إيواء',             'Dialysis (IP)',                'INPATIENT',      true, NOW(), NOW()),
  ('CAT-IP-ICU',  'عناية فائقة - إيواء',           'عناية فائقة - إيواء',           'ICU / CCU (IP)',               'INPATIENT',      true, NOW(), NOW()),
  -- Operating-room packages
  ('CAT-OR-GEN',  'غرفة عمليات - عامة',           'غرفة عمليات - عامة',           'OR General Packages',         'OPERATING_ROOM', true, NOW(), NOW()),
  ('CAT-OR-VASC', 'غرفة عمليات - أوعية دموية',    'غرفة عمليات - أوعية دموية',    'OR Vascular Packages',        'OPERATING_ROOM', true, NOW(), NOW()),
  ('CAT-OR-PLAST','غرفة عمليات - تجميل',           'غرفة عمليات - تجميل',           'OR Plastic Surgery',          'OPERATING_ROOM', true, NOW(), NOW()),
  -- Outpatient clinical specialties
  ('CAT-OP-CONS', 'كشف وزيارات - عيادة',          'كشف وزيارات - عيادة',          'Consultation (OP)',            'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-LAB',  'تحاليل ومختبرات - عيادة',      'تحاليل ومختبرات - عيادة',      'Lab & Tests (OP)',             'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-IMG',  'أشعة تخصصية - عيادة',          'أشعة تخصصية - عيادة',          'Imaging MRI/CT (OP)',          'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-PHYS', 'علاج طبيعي - عيادة',           'علاج طبيعي - عيادة',           'Physiotherapy (OP)',           'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-DENT', 'أسنان - عيادة',                'أسنان - عيادة',                'Dental (OP)',                  'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-OPT',  'نظارات طبية - عيادة',          'نظارات طبية - عيادة',          'Optical (OP)',                 'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-DRUG', 'أدوية بوصفة - عيادة',          'أدوية بوصفة - عيادة',          'Prescription Drugs (OP)',      'OUTPATIENT',     true, NOW(), NOW()),
  ('CAT-OP-PAIN', 'علاج ألم - عيادة',             'علاج ألم - عيادة',             'Pain Management (OP)',         'OUTPATIENT',     true, NOW(), NOW()),
  -- Emergency
  ('CAT-EM-AMB',  'إسعاف محلي - طوارئ',           'إسعاف محلي - طوارئ',           'Local Ambulance (EM)',         'EMERGENCY',      true, NOW(), NOW()),
  ('CAT-EM-EVAC', 'إخلاء طبي - طوارئ',            'إخلاء طبي - طوارئ',            'Medical Evacuation (EM)',      'EMERGENCY',      true, NOW(), NOW()),
  -- Special benefits
  ('CAT-SP-CHR',  'أدوية مزمنة - منافع خاصة',     'أدوية مزمنة - منافع خاصة',     'Chronic Drugs (Special)',      'SPECIAL',        true, NOW(), NOW()),
  ('CAT-SP-OCC',  'إصابات عمل - منافع خاصة',      'إصابات عمل - منافع خاصة',      'Work Injuries (Special)',      'SPECIAL',        true, NOW(), NOW()),
  ('CAT-SP-PSY',  'طب نفسي - منافع خاصة',         'طب نفسي - منافع خاصة',         'Psychiatry (Special)',         'SPECIAL',        true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- ROLLBACK SCRIPT (keep for reference)
-- ============================================================================
-- ALTER TABLE medical_categories DROP COLUMN IF EXISTS context;
-- DELETE FROM medical_categories WHERE code LIKE 'CAT-IP-%'
--                                   OR code LIKE 'CAT-OR-%'
--                                   OR code LIKE 'CAT-OP-%'
--                                   OR code LIKE 'CAT-EM-%'
--                                   OR code LIKE 'CAT-SP-%';
-- ============================================================================
