-- ============================================================
-- V106: Seed 8 Root Medical Categories (Insurance Contexts)
-- ============================================================

-- 1. Ensure the sequences are aware of new manual IDs or just let them generate
-- We use business codes for reliability.

-- ROOT CATEGORIES (8 context roots)
INSERT INTO medical_categories (code, name, name_ar, context, parent_id, active)
VALUES 
('CAT-OUTPAT', 'Outpatient', 'خارج المستشفى (OP)', 'OUTPATIENT', NULL, true),
('CAT-INPAT', 'Inpatient', 'داخل المستشفى (IP)', 'INPATIENT', NULL, true),
('CAT-DENTAL', 'Dental', 'الأسنان', 'ANY', NULL, true),
('CAT-VISION', 'Vision', 'العيون', 'ANY', NULL, true),
('CAT-MATERNITY', 'Maternity', 'الأمومة', 'ANY', NULL, true),
('CAT-CHRONIC', 'Chronic Diseases', 'الأمراض المزمنة', 'ANY', NULL, true),
('CAT-EMERGENCY', 'Emergency', 'الحالات الطارئة', 'EMERGENCY', NULL, true),
('CAT-OTHER', 'Other Services', 'أخرى', 'ANY', NULL, true)
ON CONFLICT (code) DO UPDATE 
SET name_ar = EXCLUDED.name_ar, context = EXCLUDED.context;

-- SUB CATEGORIES (Standard items from Jalyana policy for override logic)
-- We'll attach these to OP as a starting point, but they can be used with any context override.

WITH roots AS (SELECT id, code FROM medical_categories WHERE parent_id IS NULL)
INSERT INTO medical_categories (code, name, name_ar, context, parent_id, active)
SELECT 'SUB-LAB-ROUTINE', 'Routine Lab & X-Ray', 'التحاليل والأشعة الروتينية', 'ANY', id, true FROM roots WHERE code = 'CAT-OUTPAT' UNION ALL
SELECT 'SUB-IMAGING-SPEC', 'Special Imaging (MRI/CT)', 'أشعة تخصصية (MRI/CT/Scan)', 'ANY', id, true FROM roots WHERE code = 'CAT-OUTPAT' UNION ALL
SELECT 'SUB-DRUGS-RX', 'Prescription Drugs', 'الأدوية (وصفة طبية)', 'ANY', id, true FROM roots WHERE code = 'CAT-OUTPAT' UNION ALL
SELECT 'SUB-MED-DEVICES', 'Medical Devices', 'الأجهزة والمعدات الطبية', 'ANY', id, true FROM roots WHERE code = 'CAT-OUTPAT' UNION ALL
SELECT 'SUB-PHYSIO-EXT', 'External Physiotherapy', 'العلاج الطبيعي (خارجي)', 'ANY', id, true FROM roots WHERE code = 'CAT-OUTPAT' UNION ALL
SELECT 'SUB-DENTAL-ROUTINE', 'Routine Dental', 'أسنان روتيني', 'ANY', id, true FROM roots WHERE code = 'CAT-DENTAL' UNION ALL
SELECT 'SUB-DENTAL-ADVANCED', 'Advanced Dental', 'أسنان متقدم', 'ANY', id, true FROM roots WHERE code = 'CAT-DENTAL' UNION ALL
SELECT 'SUB-VISION-GLASSES', 'Medical Glasses', 'نظارات طبية', 'ANY', id, true FROM roots WHERE code = 'CAT-VISION' UNION ALL
SELECT 'SUB-MATERNITY-DELIVERY', 'Delivery (Normal/C-Section)', 'الولادة (طبيعية/قيصرية)', 'ANY', id, true FROM roots WHERE code = 'CAT-MATERNITY'
ON CONFLICT (code) DO NOTHING;
