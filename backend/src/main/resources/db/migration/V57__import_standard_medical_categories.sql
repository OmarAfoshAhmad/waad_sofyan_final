-- =================================================================================
-- V57: Import EXACT Medical Categories and Rules from Local Database
-- Description: Syncs 33 medical categories and their corresponding benefit rules
--              to match the user's local system exactly as shown in screenshots.
-- =================================================================================

-- 1. Insert/Update all 33 Categories with exact names from screenshots
INSERT INTO medical_categories (code, name, name_ar, context, active, created_at, updated_at)
VALUES
    ('CAT031', 'علاج الاسنان ( تركيب -تقويم- زراعة )', 'علاج الاسنان ( تركيب -تقويم- زراعة )', 'OUTPATIENT', true, NOW(), NOW()),
    ('CAT030', 'نظارة طبية', 'نظارة طبية', 'OUTPATIENT', true, NOW(), NOW()),
    ('CAT029', 'كشوف العيون', 'كشوف العيون', 'OUTPATIENT', true, NOW(), NOW()),
    ('CAT028', 'علاج الاسنان الروتيني ( كشف- خلع- حشو- تنظيف )', 'علاج الاسنان الروتيني ( كشف- خلع- حشو- تنظيف )', 'OUTPATIENT', true, NOW(), NOW()),
    ('CAT027', 'العلاج الطبيعي المقرَّر', 'العلاج الطبيعي المقرَّر', 'ANY', true, NOW(), NOW()),
    ('CAT026', 'الاجهزه و المعدات الطبية و فق تقرير الطبيب المختص', 'الاجهزه و المعدات الطبية و فق تقرير الطبيب المختص', 'ANY', true, NOW(), NOW()),
    ('CAT025', 'العلاجات و الادوية الروتينية وفق الوصفة الطبية', 'العلاجات و الادوية الروتينية وفق الوصفة الطبية', 'OUTPATIENT', true, NOW(), NOW()),
    ('CAT024', 'التصوير بالرنين المغناطيسي و المقطعي و الاشعة التشخيصية', 'التصوير بالرنين المغناطيسي و المقطعي و الاشعة التشخيصية', 'ANY', true, NOW(), NOW()),
    ('CAT023', 'رسوم اخصائيين و ممارسي مهنة الطب , العلاج النفسي , تحاليل و مختبرات و اشعة سينية و اشعة تشخيصية', 'رسوم اخصائيين و ممارسي مهنة الطب , العلاج النفسي , تحاليل و مختبرات و اشعة سينية و اشعة تشخيصية', 'ANY', true, NOW(), NOW()),
    ('CAT022', 'مضاعفات الحمل و الولادة', 'مضاعفات الحمل و الولادة', 'ANY', true, NOW(), NOW()),
    ('CAT021', 'الولادة الطبيعية و القيصرية', 'الولادة الطبيعية و القيصرية', 'ANY', true, NOW(), NOW()),
    ('CAT020', 'تكلفة السفر لاحد افراد عائلة المؤمن عليه في حالة الاخلاء', 'تكلفة السفر لاحد افراد عائلة المؤمن عليه في حالة الاخلاء', 'SPECIAL', true, NOW(), NOW()),
    ('CAT019', 'تكلفة شخص مرافق واحد للشخص الذي تم اخلاءه', 'تكلفة شخص مرافق واحد للشخص الذي تم اخلاءه', 'SPECIAL', true, NOW(), NOW()),
    ('CAT018', 'الاخلاء الطبي', 'الاخلاء الطبي', 'SPECIAL', true, NOW(), NOW()),
    ('CAT017', 'الغسيل الكلوي', 'الغسيل الكلوي', 'SPECIAL', true, NOW(), NOW()),
    ('CAT016', 'الاورام ( داخل المستشفى , خارج المستشفى )', 'الاورام ( داخل المستشفى , خارج المستشفى )', 'SPECIAL', true, NOW(), NOW()),
    ('CAT015', 'جراحة للمريض خارج المستشفى', 'جراحة للمريض خارج المستشفى', 'ANY', true, NOW(), NOW()),
    ('CAT014', 'الطب النفسي ( أدوية وجلسات )', 'الطب النفسي ( أدوية وجلسات )', 'ANY', true, NOW(), NOW()),
    ('CAT013', 'زرع الاعضاء', 'زرع الاعضاء', 'SPECIAL', true, NOW(), NOW()),
    ('CAT012', 'التصوير بالاشعة و تحليل العينات و الفحوص التشخيصية', 'التصوير بالاشعة و تحليل العينات و الفحوص التشخيصية', 'ANY', true, NOW(), NOW()),
    ('CAT011', 'التصوير بالرنين المغناطيسي و المقطعي و الطبقي', 'التصوير بالرنين المغناطيسي و المقطعي و الطبقي', 'ANY', true, NOW(), NOW()),
    ('CAT010', 'اصابات العمل', 'اصابات العمل', 'ANY', true, NOW(), NOW()),
    ('CAT009', 'العلاج الطبيعي', 'العلاج الطبيعي', 'ANY', true, NOW(), NOW()),
    ('CAT008', 'التمريض في المنزل أو النقاهة ( بديل الاقامة بعد الخروج )', 'التمريض في المنزل أو النقاهة ( بديل الاقامة بعد الخروج )', 'ANY', true, NOW(), NOW()),
    ('CAT007', 'الاسعاف المحلي', 'الاسعاف المحلي', 'ANY', true, NOW(), NOW()),
    ('CAT006', 'العيادات الخارجية (عام)', 'العيادات الخارجية (عام)', 'OUTPATIENT', true, NOW(), NOW()),
    ('CAT005', 'الاقامة بالسرير (درجة أولى)', 'الاقامة بالسرير (درجة أولى)', 'INPATIENT', true, NOW(), NOW()),
    ('CAT004', 'الولادة الطبيعية', 'الولادة الطبيعية', 'INPATIENT', true, NOW(), NOW()),
    ('CAT003', 'الولادة القيصرية', 'الولادة القيصرية', 'INPATIENT', true, NOW(), NOW()),
    ('CAT002', 'خدمات القلب والقسطرة', 'خدمات القلب والقسطرة', 'ANY', true, NOW(), NOW()),
    ('CAT001', 'خدمات العظام والمفاصل', 'خدمات العظام والمفاصل', 'ANY', true, NOW(), NOW()),
    
    -- SUB codes for policy UI
    ('SUB-INPAT-GENERAL', 'الإيواء - عام', 'الإيواء - عام', 'INPATIENT', true, NOW(), NOW()),
    ('SUB-INPAT-HOME-NURSING', 'الإيواء - تمريض منزلي', 'الإيواء - تمريض منزلي', 'INPATIENT', true, NOW(), NOW()),
    ('SUB-INPAT-PHYSIO', 'الإيواء - علاج طبيعي', 'الإيواء - علاج طبيعي', 'INPATIENT', true, NOW(), NOW()),
    ('SUB-INPAT-WORK-INJ', 'الإيواء - إصابات عمل', 'الإيواء - إصابات عمل', 'INPATIENT', true, NOW(), NOW()),
    ('SUB-INPAT-PSYCH', 'الإيواء - طب نفسي', 'الإيواء - طب نفسي', 'INPATIENT', true, NOW(), NOW()),
    ('SUB-INPAT-DELIVERY', 'الإيواء - ولادة طبيعية وقيصرية', 'الإيواء - ولادة طبيعية وقيصرية', 'INPATIENT', true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE
SET name    = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    active  = true;

-- 2. Create the Default Policy
INSERT INTO benefit_policies (name, description, annual_limit, start_date, end_date, active, created_at, updated_at)
VALUES ('الوثيقة القياسية', 'وثيقة المنفعة الافتراضية للنظام مع إعدادات التغطية القياسية', 50000.00, '2024-01-01', '2030-12-31', true, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- 3. Populate Rules with exact values from screenshots
DO $$
DECLARE
    policy_id BIGINT;
BEGIN
    SELECT id INTO policy_id FROM benefit_policies WHERE name = 'الوثيقة القياسية' LIMIT 1;
    
    IF policy_id IS NOT NULL THEN
        -- Rules with Specific Percentages/Limits
        -- CAT031: 50%
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, active)
        SELECT policy_id, id, 50.00, true FROM medical_categories WHERE code = 'CAT031'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 50.00;

        -- CAT028: 75%
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, active)
        SELECT policy_id, id, 75.00, true FROM medical_categories WHERE code = 'CAT028'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00;

        -- CAT027: 75%, 20 times
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, times_limit, active)
        SELECT policy_id, id, 75.00, 20, true FROM medical_categories WHERE code = 'CAT027'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, times_limit = 20;

        -- CAT026: 75%, 1 time, 1500 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, times_limit, amount_limit, active)
        SELECT policy_id, id, 75.00, 1, 1500.00, true FROM medical_categories WHERE code = 'CAT026'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, times_limit = 1, amount_limit = 1500.00;

        -- CAT025: 75%, 15000 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 15000.00, true FROM medical_categories WHERE code = 'CAT025'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 15000.00;

        -- CAT024: 75%, 1500 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 1500.00, true FROM medical_categories WHERE code = 'CAT024'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 1500.00;

        -- CAT023: 75%, 3000 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 3000.00, true FROM medical_categories WHERE code = 'CAT023'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 3000.00;

        -- CAT022: 75%, 1500 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 1500.00, true FROM medical_categories WHERE code = 'CAT022'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 1500.00;

        -- CAT021: 75%, 4000 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 4000.00, true FROM medical_categories WHERE code = 'CAT021'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 4000.00;

        -- CAT009: 75%, 10000 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 10000.00, true FROM medical_categories WHERE code = 'CAT009'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 10000.00;

        -- CAT008: 75%, 1000 limit
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active)
        SELECT policy_id, id, 75.00, 1000.00, true FROM medical_categories WHERE code = 'CAT008'
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = 75.00, amount_limit = 1000.00;

        -- All others marked as "Default" in screenshot (NULL coverage_percent to use policy default)
        INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, active)
        SELECT policy_id, id, NULL, true FROM medical_categories 
        WHERE code IN ('CAT029', 'CAT020', 'CAT019', 'CAT018', 'CAT017', 'CAT016', 'CAT015', 'CAT014', 'CAT013', 'CAT012', 'CAT011', 'CAT010', 'CAT007')
        ON CONFLICT ON CONSTRAINT uk_bpr_policy_category DO UPDATE SET coverage_percent = NULL;

    END IF;
END $$;
