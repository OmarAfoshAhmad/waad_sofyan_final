-- Refined Benefit Policy Update for Jalyana (System Overhaul)
UPDATE benefit_policies SET annual_limit = 60000.00, default_coverage_percent = 75, status = 'ACTIVE' WHERE id = 1;
DELETE FROM benefit_policy_rules WHERE benefit_policy_id = 1;

INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000.00, NULL, 'سقف الكشوفات والأخصائيين والتحاليل خارج المستشفى', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-OP';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'الإيواء والعلاج داخل المستشفى (بدون سقف عام)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-IP';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'خدمات الأسنان العامة', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-DENT';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 4000.00, NULL, 'سقف الولادة الطبيعية والقيصرية', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-MAT';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000.00, NULL, 'كشوفات الأخصائيين والممارسين (يتبع سقف العيادات)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-CONSULT';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000.00, NULL, 'التحاليل والمختبرات (يتبع سقف العيادات)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-LAB';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'الأشعة في العيادات الخارجية (بدون سقف مخصص)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-RAD';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 15000.00, NULL, 'الأدوية والعلاجات الروتينية', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-PHARMA';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 1500.00, NULL, 'قاعدة مستقلة للإقامة والتمريض (سقف استثنائي)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-STAY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'العمليات الجراحية والتخدير', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-SURGERY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'العناية الفائقة ICU/CCU', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-ICU';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 10000.00, 20, 'العلاج الطبيعي (بحد أقصى 20 زيارة)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-PHYSIO';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'كشف - خلع - حشو - تنظيف', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DENT-REG';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 50, NULL, NULL, 'تركيب - تقويم - زراعة', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DENT-COS';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 500.00, NULL, 'النظارات الطبية (نظارة واحدة في السنة)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-VISION';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 4000.00, NULL, 'الولادة ومتابعة الحمل', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-MATERNITY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'حالات الطوارئ والإسعاف', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-EMERGENCY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'الإسعاف المحلي', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-AMBULANCE';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 1500.00, NULL, 'الأجهزة والمعدات الطبية', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-SUPPLIES';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000.00, NULL, 'الطب النفسي (أدوية وجلسات)', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-PSYCH';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, NULL, 'غسيل الكلى', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DIALYSIS';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 100, NULL, NULL, 'علاج الأورام - تغطية كاملة', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-ONCOLOGY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 25000.00, NULL, 'إصابات العمل', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-WORK-INJ';