-- =================================================================================
-- V57: Import Medical Categories (33 Categories)
-- Description: Syncs medical categories to match the production/reference database
--              with codes CAT001 through CAT033 and policy sub-categories.
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

-- 2. Cleanup roots and re-link
DELETE FROM medical_category_roots;

INSERT INTO medical_category_roots (category_id, root_id)
SELECT id, id FROM medical_categories WHERE active = true;
